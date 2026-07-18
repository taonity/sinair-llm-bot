import { WsChat, WsChatEvents, UserStatus, MessageStyle } from '@iassasin/wschatapi';
import { readFileSync, writeFileSync, rmSync } from 'fs';
import { config } from './config.js';
import { logger } from './logger.js';
import { bufferMessage, bufferEvent, startFlushTimer, stopFlushTimer } from './batcher.js';
import { startSender, stopSender } from './sender.js';
import { startPresence, stopPresence } from './presence.js';
import { startTyping, stopTyping } from './typing.js';

let chat = null;
let reconnectTimeout = null;
// Session token returned by the last successful auth. On reconnect we use it with
// restoreConnection() to reclaim the orphaned session the server keeps alive during its
// disconnect debounce window, instead of opening a competing fresh login that collides
// with the orphan and gets dropped.
let sessionToken = null;
// Set during graceful shutdown so we close cleanly and stop the reconnect loop.
let shuttingDown = false;
const roomsByTarget = new Map();
// Per-room history warm-up state. While a room is warming up (right after a join, when the server
// replays its history burst) incoming messages are tagged `historical` so the backend stores them
// without re-running commands or reacting. Value: { idle: Timeout, hard: Timeout }.
const historyWarmup = new Map();
// Per-room join cutoff (unixtime, seconds, SERVER clock). Any message whose server-side `time` is
// at or before the moment we (re)joined the room is a history replay, never something posted to us
// live. This is a deterministic backstop to the idle-based warm-up window: the last replayed message
// can arrive after the idle timer (or hard cap) has already closed the window, and this cutoff still
// catches it so the bot never reacts to anything from before it joined. The cutoff is anchored to
// the SERVER's clock (derived from the room's online list at join, see serverTimeAtJoin) rather than
// our local clock: the chat server is remote, so a collector clock that lags the server would
// otherwise place a just-posted history message *after* our local join time and leak it as live.
// Value: unixtime seconds.
const roomJoinedAt = new Map();

// Heartbeat/watchdog timers for the current connection (see startHeartbeat). lastPongAt tracks the
// last time we saw proof the link is alive (a pong or any inbound server traffic).
let heartbeatTimer = null;
let heartbeatWatchdog = null;
let lastPongAt = 0;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Reads the persisted session token (best-effort). Returns null if absent or unreadable. */
function loadToken() {
    try {
        return readFileSync(config.tokenFile, 'utf-8').trim() || null;
    } catch {
        return null;
    }
}

/** Persists the session token so an ungraceful restart can restore the orphan (best-effort). */
function saveToken(token) {
    if (!token) return;
    try {
        writeFileSync(config.tokenFile, token, 'utf-8');
    } catch (err) {
        logger.warn(`[collector] Could not persist session token to ${config.tokenFile}: ${err?.message || err}`);
    }
}

/** Drops the persisted token (used on graceful shutdown, when the server removes our session). */
function clearToken() {
    sessionToken = null;
    try {
        rmSync(config.tokenFile, { force: true });
    } catch {
        /* best-effort */
    }
}

/** Sends a message to a joined room. Returns false if the room is not currently joined. */
function sendChatMessage(target, text) {
    const room = roomsByTarget.get(target);
    if (!room) return false;
    room.sendMessage(text);
    return true;
}

/** Registers a joined room and (re)asserts the bot's nick/color in it. */
function onRoomReady(room) {
    roomsByTarget.set(room.target, room);
    roomJoinedAt.set(room.target, serverTimeAtJoin(room));
    if (config.botColor) {
        room.sendMessage(`/color ${config.botColor}`);
        logger.info(`[collector] Set color '${config.botColor}' in ${room.target}`);
    }
    if (config.botNick) {
        room.sendMessage(`/nick ${config.botNick}`);
        logger.info(`[collector] Set nick '${config.botNick}' in ${room.target}`);
    }
    startHistoryWarmup(room.target);
    logger.info(`[collector] Room ready: ${room.target}`);
}

/** True while the room is replaying its post-join history burst. */
function isHistoryWarmup(target) {
    return historyWarmup.has(target);
}

/**
 * The server's notion of "now" at the moment we joined the room, in unixtime seconds. Derived from
 * the room's online list (each member carries a server-stamped `last_seen_time`) so the join cutoff
 * is on the SAME clock as incoming message timestamps, immune to skew between the collector and the
 * remote chat server. Falls back to (and never goes below) our local clock when the online list has
 * no usable timestamps.
 */
function serverTimeAtJoin(room) {
    const local = Math.floor(Date.now() / 1000);
    let maxSeen = 0;
    for (const member of room?.members || []) {
        const seen = member?.last_seen_time;
        if (typeof seen === 'number' && seen > maxSeen) maxSeen = seen;
    }
    return Math.max(local, maxSeen);
}

/**
 * True if a message was sent at or before we (re)joined the room, i.e. it's a history replay rather
 * than a live message directed at the bot. Deterministic backstop to the idle-based warm-up window.
 */
function isBeforeJoin(target, sentAt) {
    const joinedAt = roomJoinedAt.get(target);
    return joinedAt != null && typeof sentAt === 'number' && sentAt <= joinedAt;
}

/** Begins the history warm-up window for a room, ended by idle quiet or a hard cap. */
function startHistoryWarmup(target) {
    finishHistoryWarmup(target);
    const state = {
        hard: setTimeout(() => finishHistoryWarmup(target), config.historyWarmupMaxMs),
        idle: null,
    };
    historyWarmup.set(target, state);
    bumpHistoryWarmup(target);
}

/** Extends the idle deadline; each history message defers the end of the warm-up window. */
function bumpHistoryWarmup(target) {
    const state = historyWarmup.get(target);
    if (!state) return;
    if (state.idle) clearTimeout(state.idle);
    state.idle = setTimeout(() => finishHistoryWarmup(target), config.historyWarmupIdleMs);
}

function finishHistoryWarmup(target) {
    const state = historyWarmup.get(target);
    if (!state) return;
    if (state.idle) clearTimeout(state.idle);
    if (state.hard) clearTimeout(state.hard);
    historyWarmup.delete(target);
    logger.debug(`[collector] History warm-up ended for ${target}`);
}

function clearAllHistoryWarmup() {
    for (const target of [...historyWarmup.keys()]) finishHistoryWarmup(target);
    roomJoinedAt.clear();
}

/**
 * Reflects the bot's presence in a joined room via the member away/back status toggle. Returns
 * false if the room is not currently joined.
 */
function setRoomPresence(target, presence) {
    const room = roomsByTarget.get(target);
    if (!room) return false;
    room.changeStatus(presence === 'back' ? UserStatus.back : UserStatus.away);
    return true;
}

function setRoomNick(target, nick) {
    const room = roomsByTarget.get(target);
    if (!room) return false;
    room.sendMessage(`/nick ${nick}`);
    return true;
}

/**
 * Toggles the bot's typing indicator in a joined room while it composes a reply. Returns false if
 * the room is not currently joined.
 */
function setRoomTyping(target, isTyping) {
    const room = roomsByTarget.get(target);
    if (!room) return false;
    room.changeStatus(isTyping ? UserStatus.typing : UserStatus.stop_typing);
    return true;
}

export async function startCollector() {
    logger.info(`[collector] Connecting to ${config.chatWsUrl}...`);
    chat = new WsChat(config.chatWsUrl);

    chat.on(WsChatEvents.open, () => {
        logger.info('[collector] Connected to chat server');
    });

    chat.on(WsChatEvents.close, () => {
        logger.warn('[collector] Disconnected from chat server');
        stopHeartbeat();
        stopFlushTimer();
        stopSender();
        stopPresence();
        stopTyping();
        clearAllHistoryWarmup();
        roomsByTarget.clear();
        if (!shuttingDown) scheduleReconnect();
    });

    chat.on(WsChatEvents.connectionError, (err) => {
        logger.error('[collector] Connection error:', err);
        scheduleReconnect();
    });

    chat.on(WsChatEvents.error, (err) => {
        logger.error('[collector] Chat error:', err);
    });

    // Fired when a room is joined without an explicit joinRoom() call, i.e. the rooms the
    // server auto-rejoins us to after restoreConnection() reclaims the orphaned session.
    chat.on(WsChatEvents.joinRoom, (room) => {
        logger.info(`[collector] Auto-rejoined room after session restore: ${room.target}`);
        onRoomReady(room);
    });

    chat.on(WsChatEvents.message, (room, msgobj) => {
        markAlive();
        const member = room?.getMemberById?.(msgobj.from);
        const warmup = isHistoryWarmup(room?.target);
        const beforeJoin = isBeforeJoin(room?.target, msgobj.time);
        const historical = warmup || beforeJoin;
        // Diagnostic: log the exact inputs to the historical decision (no message text) so a
        // post-rejoin re-answer can be traced to warm-up/clock-skew leakage. from_login is a nick
        // (identifier), id/time/joinedAt are not content.
        logger.debug(
            `[collector] message event — room=${room?.target}, from=${msgobj?.from_login}, ` +
            `id=${msgobj?.id ?? 'none'}, time=${msgobj?.time}, joinedAt=${roomJoinedAt.get(room?.target) ?? 'none'}, ` +
            `warmup=${warmup}, beforeJoin=${beforeJoin}, historical=${historical}`,
        );
        if (warmup) bumpHistoryWarmup(room.target);
        const dto = {
            externalId: msgobj.id || null,
            roomTarget: msgobj.target,
            senderMemberId: msgobj.from,
            senderUserId: member?.user_id || 0,
            senderLogin: msgobj.from_login,
            senderColor: msgobj.color || null,
            messageText: msgobj.message,
            messageStyle: resolveMessageStyle(msgobj.style),
            recipientMemberId: msgobj.to || 0,
            sentAt: msgobj.time,
            historical,
        };
        bufferMessage(dto);
    });

    chat.on(WsChatEvents.userStatusChange, (room, userobj) => {
        markAlive();
        logger.debug(`[collector] userStatusChange — room=${room?.target}, member=${userobj?.name}, status=${userobj?.status} (raw=${JSON.stringify(userobj)})`);

        // Skip transient statuses that don't add training value
        if (userobj.status === UserStatus.typing || userobj.status === UserStatus.stop_typing
            || userobj.status === UserStatus.away || userobj.status === UserStatus.back) {
            logger.debug(`[collector] Skipping transient status: ${userobj.status}`);
            return;
        }

        const dto = {
            roomTarget: room.target,
            memberId: userobj.member_id,
            userId: userobj.user_id || 0,
            memberName: userobj.name,
            memberColor: userobj.color || null,
            status: resolveUserStatus(userobj.status),
            eventData: userobj.data || null,
            isGirl: userobj.girl || false,
            isModer: userobj.is_moder || false,
            isOwner: userobj.is_owner || false,
            eventTime: userobj.last_seen_time || Math.floor(Date.now() / 1000),
        };
        logger.debug(`[collector] Buffering event: status=${dto.status}, member=${dto.memberName}, room=${dto.roomTarget}`);
        bufferEvent(dto);
    });

    try {
        await chat.open();

        let restored = false;
        if (!sessionToken) sessionToken = loadToken();
        if (sessionToken) {
            try {
                logger.info('[collector] Restoring previous session (reclaiming orphan)...');
                const auth = await chat.restoreConnection(sessionToken);
                if (auth?.user_id) {
                    // Revive succeeded: the server transferred our old session (user_id > 0)
                    // and auto-rejoins its rooms via joinRoom events.
                    restored = true;
                    sessionToken = auth.token || sessionToken;
                    saveToken(sessionToken);
                    // Give the auto-rejoin joinRoom events a moment to populate roomsByTarget
                    // before we fill any gaps below.
                    await sleep(config.restoreRejoinGrace);
                    logger.info(`[collector] Session restored (user_id=${auth.user_id})`);
                } else {
                    // Orphan already gone: the server silently handed us a fresh guest session
                    // (user_id 0, no rooms). Fall through to a normal api-key auth below.
                    logger.warn('[collector] Orphan expired (guest session returned); re-authenticating');
                    sessionToken = null;
                }
            } catch (err) {
                logger.warn(`[collector] Session restore failed, re-authenticating: ${err?.info || err?.message || err}`);
                sessionToken = null;
            }
        }

        if (!restored) {
            const auth = await chat.authByApiKey(config.chatApiKey);
            sessionToken = auth?.token || null;
            saveToken(sessionToken);
            logger.info(`[collector] Authenticated successfully (user_id=${auth?.user_id ?? 'unknown'})`);
        }

        // Join any configured room not already joined. On a fresh auth this is all of them;
        // after a restore it's only the rooms the server did not auto-rejoin us to.
        for (const roomTarget of config.chatRooms) {
            if (roomsByTarget.has(roomTarget)) continue;
            const room = await chat.joinRoom(roomTarget, { autoLogin: true, loadHistory: true });
            onRoomReady(room);
        }

        startFlushTimer();
        startSender(sendChatMessage);
        startPresence(setRoomPresence, setRoomNick);
        startTyping(setRoomTyping);
        startHeartbeat();
    } catch (err) {
        logger.error('[collector] Failed to initialize:', err);
        scheduleReconnect();
    }
}

/**
 * Starts the liveness heartbeat for the current connection: periodically pings the socket and, if no
 * pong (or any inbound traffic) is observed within the timeout, treats the connection as dead and
 * forces a reconnect. This covers silent drops (NAT/idle timeouts, half-open TCP, the remote chat
 * server vanishing) where no WebSocket close frame is ever delivered, so the `close` handler never
 * fires and the collector would otherwise sit on a dead socket forever.
 */
function startHeartbeat() {
    stopHeartbeat();
    const sock = chat?._sock;
    if (!sock) return;
    markAlive();
    // ws answers our ping frame with a pong; any pong proves the link is alive end-to-end.
    sock.on('pong', markAlive);
    heartbeatTimer = setInterval(() => {
        const active = chat?._sock;
        if (!active || active.readyState !== active.OPEN) return;
        try {
            active.ping();
        } catch (err) {
            logger.debug(`[collector] Heartbeat ping failed: ${err?.message || err}`);
        }
    }, config.heartbeatIntervalMs);
    heartbeatWatchdog = setInterval(() => {
        if (shuttingDown) return;
        if (Date.now() - lastPongAt > config.heartbeatTimeoutMs) {
            logger.warn('[collector] Heartbeat timeout — connection appears dead, forcing reconnect');
            forceReconnect();
        }
    }, config.heartbeatIntervalMs);
}

/** Records inbound liveness (a pong or any server message) so the watchdog holds off. */
function markAlive() {
    lastPongAt = Date.now();
}

function stopHeartbeat() {
    if (heartbeatTimer) {
        clearInterval(heartbeatTimer);
        heartbeatTimer = null;
    }
    if (heartbeatWatchdog) {
        clearInterval(heartbeatWatchdog);
        heartbeatWatchdog = null;
    }
}

/** Tears down a connection the watchdog judged dead and drives the normal reconnect path. */
function forceReconnect() {
    stopHeartbeat();
    const sock = chat?._sock;
    if (sock) {
        // terminate() destroys the half-open socket at once and fires the ws 'close' event, which
        // drives our close handler (cleanup + scheduleReconnect).
        try {
            sock.terminate();
        } catch (err) {
            logger.debug(`[collector] Error terminating dead socket: ${err?.message || err}`);
        }
    } else if (!shuttingDown) {
        scheduleReconnect();
    }
}

function scheduleReconnect() {
    if (shuttingDown || reconnectTimeout) return;
    const delay = 10000;
    logger.info(`[collector] Reconnecting in ${delay / 1000}s...`);
    reconnectTimeout = setTimeout(async () => {
        reconnectTimeout = null;
        try {
            await startCollector();
        } catch (err) {
            logger.error('[collector] Reconnect failed:', err);
            scheduleReconnect();
        }
    }, delay);
}

/**
 * Gracefully shuts the collector down: stops the reconnect loop and closes the chat socket with
 * a normal (1000) close frame so the server removes our client immediately instead of leaving a
 * lingering orphan that blocks the next start from reconnecting.
 */
export async function stopCollector() {
    shuttingDown = true;
    if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
        reconnectTimeout = null;
    }
    stopHeartbeat();
    stopFlushTimer();
    stopSender();
    stopPresence();
    stopTyping();
    if (chat?.connected) {
        try {
            // Bound the close so an unresponsive socket can't block past the stop grace period.
            await Promise.race([chat.close(), sleep(config.shutdownCloseTimeout)]);
            logger.info('[collector] Chat connection closed cleanly');
        } catch (err) {
            logger.warn(`[collector] Error closing chat connection: ${err?.message || err}`);
        }
    }
    // The server removed our session on a clean (1000) close, so the token is now invalid.
    clearToken();
    clearAllHistoryWarmup();
    roomsByTarget.clear();
}

function resolveMessageStyle(style) {
    switch (style) {
        case MessageStyle.message: return 'message';
        case MessageStyle.me: return 'me';
        case MessageStyle.event: return 'event';
        case MessageStyle.offtop: return 'offtop';
        default: return 'unknown';
    }
}

function resolveUserStatus(status) {
    switch (status) {
        case UserStatus.online: return 'online';
        case UserStatus.offline: return 'offline';
        case UserStatus.away: return 'away';
        case UserStatus.back: return 'back';
        case UserStatus.nick_change: return 'nick_change';
        case UserStatus.gender_change: return 'gender_change';
        case UserStatus.color_change: return 'color_change';
        case UserStatus.orphan: return 'orphan';
        default: return 'unknown';
    }
}

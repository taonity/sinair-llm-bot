import { WsChat, WsChatEvents, UserStatus, MessageStyle } from '@iassasin/wschatapi';
import { readFileSync, writeFileSync, rmSync } from 'fs';
import { config } from './config.js';
import { logger } from './logger.js';
import { bufferMessage, bufferEvent, startFlushTimer, stopFlushTimer } from './batcher.js';
import { startSender, stopSender } from './sender.js';
import { startPresence, stopPresence } from './presence.js';

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
    if (config.botColor) {
        room.sendMessage(`/color ${config.botColor}`);
        logger.info(`[collector] Set color '${config.botColor}' in ${room.target}`);
    }
    if (config.botNick) {
        room.sendMessage(`/nick ${config.botNick}`);
        logger.info(`[collector] Set nick '${config.botNick}' in ${room.target}`);
    }
    logger.info(`[collector] Room ready: ${room.target}`);
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

export async function startCollector() {
    logger.info(`[collector] Connecting to ${config.chatWsUrl}...`);
    chat = new WsChat(config.chatWsUrl);

    chat.on(WsChatEvents.open, () => {
        logger.info('[collector] Connected to chat server');
    });

    chat.on(WsChatEvents.close, () => {
        logger.warn('[collector] Disconnected from chat server');
        stopFlushTimer();
        stopSender();
        stopPresence();
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
        logger.debug(`[collector] message event — room=${room?.target}, from=${msgobj?.from_login}`);
        const member = room?.getMemberById?.(msgobj.from);
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
        };
        bufferMessage(dto);
    });

    chat.on(WsChatEvents.userStatusChange, (room, userobj) => {
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
    } catch (err) {
        logger.error('[collector] Failed to initialize:', err);
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
    stopFlushTimer();
    stopSender();
    stopPresence();
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

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
// Reclaims the server-side orphan instead of creating a colliding fresh login.
let sessionToken = null;
let shuttingDown = false;
const roomsByTarget = new Map();
const historyWarmup = new Map();
// Uses server time so clock skew cannot leak replayed history as live traffic.
const roomJoinedAt = new Map();

// Detects half-open sockets that never emit a close frame.
let heartbeatTimer = null;
let heartbeatWatchdog = null;
let lastPongAt = 0;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function loadToken() {
    try {
        return readFileSync(config.tokenFile, 'utf-8').trim() || null;
    } catch {
        return null;
    }
}

function saveToken(token) {
    if (!token) return;
    try {
        writeFileSync(config.tokenFile, token, 'utf-8');
    } catch (err) {
        logger.warn(`[collector] Could not persist session token to ${config.tokenFile}: ${err?.message || err}`);
    }
}

function clearToken() {
    sessionToken = null;
    try {
        rmSync(config.tokenFile, { force: true });
    } catch {
    }
}

function sendChatMessage(target, text) {
    const room = roomsByTarget.get(target);
    if (!room) return false;
    room.sendMessage(text);
    return true;
}

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

function isHistoryWarmup(target) {
    return historyWarmup.has(target);
}

function serverTimeAtJoin(room) {
    const local = Math.floor(Date.now() / 1000);
    let maxSeen = 0;
    for (const member of room?.members || []) {
        const seen = member?.last_seen_time;
        if (typeof seen === 'number' && seen > maxSeen) maxSeen = seen;
    }
    return Math.max(local, maxSeen);
}

function isBeforeJoin(target, sentAt) {
    const joinedAt = roomJoinedAt.get(target);
    return joinedAt != null && typeof sentAt === 'number' && sentAt <= joinedAt;
}

function startHistoryWarmup(target) {
    finishHistoryWarmup(target);
    const state = {
        hard: setTimeout(() => finishHistoryWarmup(target), config.historyWarmupMaxMs),
        idle: null,
    };
    historyWarmup.set(target, state);
    bumpHistoryWarmup(target);
}

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

    chat.on(WsChatEvents.sysMessage, (room, text) => {
        markAlive();
        logger.debug(`[collector] sysMessage — room=${room?.target}, text=${text}`);
        if (!room) return;
        const dto = {
            roomTarget: room.target,
            memberId: 0,
            userId: 0,
            memberName: '',
            memberColor: null,
            status: 'system',
            eventData: text || null,
            isGirl: false,
            isModer: false,
            isOwner: false,
            eventTime: Math.floor(Date.now() / 1000),
        };
        bufferEvent(dto);
    });

    chat.on(WsChatEvents.userStatusChange, (room, userobj) => {
        markAlive();
        logger.debug(`[collector] userStatusChange — room=${room?.target}, member=${userobj?.name}, status=${userobj?.status} (raw=${JSON.stringify(userobj)})`);

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
                    restored = true;
                    sessionToken = auth.token || sessionToken;
                    saveToken(sessionToken);
                    await sleep(config.restoreRejoinGrace);
                    logger.info(`[collector] Session restored (user_id=${auth.user_id})`);
                } else {
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

function startHeartbeat() {
    stopHeartbeat();
    const sock = chat?._sock;
    if (!sock) return;
    markAlive();
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

function forceReconnect() {
    stopHeartbeat();
    const sock = chat?._sock;
    if (sock) {
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
            await Promise.race([chat.close(), sleep(config.shutdownCloseTimeout)]);
            logger.info('[collector] Chat connection closed cleanly');
        } catch (err) {
            logger.warn(`[collector] Error closing chat connection: ${err?.message || err}`);
        }
    }
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

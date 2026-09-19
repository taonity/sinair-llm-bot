import { WsChat, WsChatEvents, UserStatus, MessageStyle } from '@iassasin/wschatapi';
import { readFileSync, writeFileSync, rmSync } from 'fs';
import { config } from './config.js';
import { logger } from './logger.js';
import { bufferMessage, bufferEvent, startFlushTimer, stopFlushTimer } from './batcher.js';
import { startSender, stopSender } from './sender.js';
import { loadPresences, persistNickname, startPresence, stopPresence } from './presence.js';
import { startTyping, stopTyping } from './typing.js';
import { estimateServerNowMs, isOlderThan, normalizeUnixTime } from './history.js';

let chat = null;
let reconnectTimeout = null;
// Reclaims the server-side orphan instead of creating a colliding fresh login.
let sessionToken = null;
let shuttingDown = false;
const roomsByTarget = new Map();
const historyWarmup = new Map();
// Uses server time so clock skew cannot leak replayed history as live traffic.
const roomJoinedAt = new Map();
const roomJoinedLocallyAt = new Map();

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
    roomJoinedLocallyAt.set(room.target, Date.now());
    if (config.botColor) {
        room.sendMessage(`/color ${config.botColor}`);
        logger.info(`[collector] Set color '${config.botColor}' in ${room.target}`);
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
        const seen = normalizeUnixTime(member?.last_seen_time);
        if (seen != null && seen > maxSeen) maxSeen = seen;
    }
    return Math.max(local, maxSeen);
}

function isBeforeJoin(target, sentAt) {
    const joinedAt = roomJoinedAt.get(target);
    const timestamp = normalizeUnixTime(sentAt);
    return joinedAt != null && timestamp != null && timestamp <= joinedAt;
}

function estimatedServerNow(target) {
    return estimateServerNowMs(roomJoinedAt.get(target), roomJoinedLocallyAt.get(target), Date.now());
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
    roomJoinedLocallyAt.clear();
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

export function chooseAvailableNickname(desiredNickname, rooms) {
    const isUsed = (nickname) => rooms.some((room) =>
        (room.members || []).some((member) =>
            member.member_id !== room.memberId &&
            String(member.name || '').localeCompare(nickname, undefined, { sensitivity: 'accent' }) === 0,
        ),
    );
    if (!isUsed(desiredNickname)) return desiredNickname;

    let suffix = 2;
    while (isUsed(`${desiredNickname}_${suffix}`)) suffix += 1;
    return `${desiredNickname}_${suffix}`;
}

function baseNickname(presences) {
    const presence = presences.find((item) => item?.nickname);
    if (!presence) return config.botNick;
    const nickname = String(presence.nickname);
    const suffix = String(presence.nickSuffix || '');
    return suffix && nickname.endsWith(suffix) ? nickname.slice(0, -suffix.length) : nickname;
}

async function applyInitialNickname() {
    let desiredNickname = config.botNick;
    try {
        desiredNickname = baseNickname(await loadPresences());
    } catch (err) {
        logger.warn(`[collector] Could not load database-backed nickname; using '${desiredNickname}': ${err?.message || err}`);
    }
    if (!desiredNickname) return;

    const rooms = [...roomsByTarget.values()];
    const availableNickname = chooseAvailableNickname(desiredNickname, rooms);
    if (availableNickname !== desiredNickname) {
        await persistNickname(availableNickname);
        logger.warn(`[collector] Nick '${desiredNickname}' is occupied; switched permanently to '${availableNickname}'`);
    }
    for (const room of rooms) {
        if (room.memberNick !== availableNickname) room.sendMessage(`/nick ${availableNickname}`);
    }
}

function setRoomTyping(target, isTyping) {
    const room = roomsByTarget.get(target);
    if (!room) return false;
    room.changeStatus(isTyping ? UserStatus.typing : UserStatus.stop_typing);
    return true;
}

export async function startCollector() {
    logger.info(`[collector] Connecting to ${config.chatWsUrl}...`);
    const client = new WsChat(config.chatWsUrl);
    chat = client;
    let initializationPhase = 'opening connection';

    client.on(WsChatEvents.open, () => {
        if (client !== chat) return;
        logger.info('[collector] Connected to chat server');
    });

    client.on(WsChatEvents.close, () => {
        if (client !== chat) {
            logger.debug('[collector] Ignoring close event from a superseded connection');
            return;
        }
        logger.warn(`[collector] Disconnected from chat server; joinedRooms=${formatRoomTargets()}`);
        stopHeartbeat();
        stopFlushTimer();
        stopSender();
        stopPresence();
        stopTyping();
        clearAllHistoryWarmup();
        roomsByTarget.clear();
        if (!shuttingDown) scheduleReconnect();
    });

    client.on(WsChatEvents.connectionError, (err) => {
        if (client !== chat) {
            logger.debug('[collector] Ignoring connection error from a superseded connection');
            return;
        }
        logConnectionFailure('Connection error', err);
        scheduleReconnect();
    });

    client.on(WsChatEvents.error, (err) => {
        if (client !== chat) return;
        logConnectionFailure('Chat error', err);
    });

    client.on(WsChatEvents.joinRoom, (room) => {
        if (client !== chat) return;
        logger.info(`[collector] Auto-rejoined room after session restore: ${room.target}`);
        onRoomReady(room);
    });

    client.on(WsChatEvents.message, (room, msgobj) => {
        if (client !== chat) return;
        markAlive();
        const member = room?.getMemberById?.(msgobj.from);
        const warmup = isHistoryWarmup(room?.target);
        const beforeJoin = isBeforeJoin(room?.target, msgobj.time);
        const tooOld = isOlderThan(msgobj.time, estimatedServerNow(room?.target), config.messageLiveMaxAgeMs);
        const historical = warmup || beforeJoin || tooOld;
        logger.debug(
            `[collector] message event — room=${room?.target}, from=${msgobj?.from_login}, ` +
            `id=${msgobj?.id ?? 'none'}, time=${msgobj?.time}, joinedAt=${roomJoinedAt.get(room?.target) ?? 'none'}, ` +
            `warmup=${warmup}, beforeJoin=${beforeJoin}, tooOld=${tooOld}, historical=${historical}`,
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

    client.on(WsChatEvents.sysMessage, (room, text) => {
        if (client !== chat) return;
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

    client.on(WsChatEvents.userStatusChange, (room, userobj) => {
        if (client !== chat) return;
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
        await client.open();

        let restored = false;
        if (!sessionToken) sessionToken = loadToken();
        if (sessionToken) {
            try {
                initializationPhase = 'restoring session';
                logger.info('[collector] Restoring previous session (reclaiming orphan)...');
                const auth = await client.restoreConnection(sessionToken);
                if (auth?.user_id) {
                    restored = true;
                    sessionToken = auth.token || sessionToken;
                    saveToken(sessionToken);
                    await sleep(config.restoreRejoinGrace);
                    logger.info(`[collector] Session restored (user_id=${auth.user_id})`);
                } else {
                    logger.warn('[collector] Orphan expired (guest session returned); re-authenticating');
                    clearToken();
                }
            } catch (err) {
                logger.warn(`[collector] Session restore failed, re-authenticating: ${err?.info || err?.message || err}`);
                sessionToken = null;
            }
        }

        if (!restored) {
            initializationPhase = 'authenticating with API key';
            const auth = await client.authByApiKey(config.chatApiKey);
            sessionToken = auth?.token || null;
            saveToken(sessionToken);
            logger.info(`[collector] Authenticated successfully (user_id=${auth?.user_id ?? 'unknown'})`);
        }

        initializationPhase = 'joining rooms';
        for (const roomTarget of config.chatRooms) {
            if (roomsByTarget.has(roomTarget)) continue;
            logger.info(`[collector] Joining room ${roomTarget}...`);
            const room = await client.joinRoom(roomTarget, { autoLogin: false, loadHistory: true });
            onRoomReady(room);
        }

        const missingRooms = config.chatRooms.filter((target) => !roomsByTarget.has(target));
        if (missingRooms.length > 0) {
            throw new Error(`Room membership incomplete; missing=${missingRooms.join(',')}`);
        }
        logger.info(`[collector] Room membership established; joinedRooms=${formatRoomTargets()}`);
        await applyInitialNickname();

        initializationPhase = 'starting background workers';
        startFlushTimer();
        startSender(sendChatMessage);
        startPresence(setRoomPresence, setRoomNick);
        startTyping(setRoomTyping);
        startHeartbeat(client);
    } catch (err) {
        logConnectionFailure(
            `Failed while ${initializationPhase}; joinedRooms=${formatRoomTargets()}`,
            err,
        );
        if (client === chat) {
            await closeFailedClient(client);
            stopHeartbeat();
            stopFlushTimer();
            stopSender();
            stopPresence();
            stopTyping();
            clearAllHistoryWarmup();
            roomsByTarget.clear();
            scheduleReconnect();
        }
    }
}

function formatRoomTargets() {
    return roomsByTarget.size > 0 ? [...roomsByTarget.keys()].join(',') : 'none';
}

function logConnectionFailure(context, err) {
    const details = [err?.name, err?.code, err?.message || err?.info || String(err)]
        .filter(Boolean)
        .filter((value, index, values) => values.indexOf(value) === index)
        .join(': ');
    logger.error(`[collector] ${context}: ${details}`);
    logger.debug(`[collector] ${context} details:`, err);
}

async function closeFailedClient(client) {
    if (!client?.connected) return;
    try {
        await Promise.race([client.close(), sleep(config.shutdownCloseTimeout)]);
    } catch (err) {
        logger.warn(`[collector] Failed to close incomplete connection: ${err?.message || err}`);
    }
}

function startHeartbeat(client) {
    stopHeartbeat();
    const socketEntry = findChatSocket(client);
    if (!socketEntry) {
        throw new Error('Could not locate the WebSocket used by the chat client');
    }
    const { socket, property } = socketEntry;
    markAlive();
    socket.on('pong', () => {
        if (client === chat) markAlive();
    });
    logger.info(
        `[collector] Heartbeat monitoring started; intervalMs=${config.heartbeatIntervalMs}, ` +
        `timeoutMs=${config.heartbeatTimeoutMs}, socketProperty=${property}`,
    );
    heartbeatTimer = setInterval(() => {
        if (client !== chat) return;
        if (socket.readyState !== socket.OPEN) {
            logger.warn(`[collector] Heartbeat found socket state=${socket.readyState}; forcing reconnect`);
            forceReconnect(client, socket);
            return;
        }
        try {
            socket.ping();
        } catch (err) {
            logger.warn(`[collector] Heartbeat ping failed; forcing reconnect: ${err?.message || err}`);
            forceReconnect(client, socket);
        }
    }, config.heartbeatIntervalMs);
    heartbeatWatchdog = setInterval(() => {
        if (shuttingDown || client !== chat) return;
        const heartbeatAgeMs = Date.now() - lastPongAt;
        if (heartbeatAgeMs > config.heartbeatTimeoutMs) {
            logger.warn(
                `[collector] Heartbeat timeout; ageMs=${heartbeatAgeMs}, socketState=${socket.readyState}, ` +
                `joinedRooms=${formatRoomTargets()}; forcing reconnect`,
            );
            forceReconnect(client, socket);
        }
    }, config.heartbeatIntervalMs);
}

function findChatSocket(client) {
    for (const [property, value] of Object.entries(client || {})) {
        if (value && typeof value.on === 'function' && typeof value.ping === 'function'
            && typeof value.terminate === 'function' && typeof value.readyState === 'number') {
            return { socket: value, property };
        }
    }
    return null;
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

function forceReconnect(client, socket) {
    if (client !== chat || shuttingDown) return;
    stopHeartbeat();
    stopFlushTimer();
    stopSender();
    stopPresence();
    stopTyping();
    clearAllHistoryWarmup();
    roomsByTarget.clear();
    try {
        socket.terminate();
    } catch (err) {
        logger.warn(`[collector] Error terminating dead socket: ${err?.message || err}`);
    }
    scheduleReconnect();
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
            logConnectionFailure('Reconnect failed', err);
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

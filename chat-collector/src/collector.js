import { WsChat, WsChatEvents, UserStatus, MessageStyle } from '@iassasin/wschatapi';
import { config } from './config.js';
import { logger } from './logger.js';
import { bufferMessage, bufferEvent, startFlushTimer, stopFlushTimer } from './batcher.js';
import { startSender, stopSender } from './sender.js';
import { startPresence, stopPresence } from './presence.js';

let chat = null;
let reconnectTimeout = null;
const roomsByTarget = new Map();

/** Sends a message to a joined room. Returns false if the room is not currently joined. */
function sendChatMessage(target, text) {
    const room = roomsByTarget.get(target);
    if (!room) return false;
    room.sendMessage(text);
    return true;
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
        scheduleReconnect();
    });

    chat.on(WsChatEvents.connectionError, (err) => {
        logger.error('[collector] Connection error:', err);
        scheduleReconnect();
    });

    chat.on(WsChatEvents.error, (err) => {
        logger.error('[collector] Chat error:', err);
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
        await chat.authByApiKey(config.chatApiKey);
        logger.info('[collector] Authenticated successfully');

        for (const roomTarget of config.chatRooms) {
            const room = await chat.joinRoom(roomTarget, { autoLogin: true, loadHistory: true });
            roomsByTarget.set(room.target, room);
            if (config.botColor) {
                room.sendMessage(`/color ${config.botColor}`);
                logger.info(`[collector] Set color '${config.botColor}' in ${room.target}`);
            }
            if (config.botNick) {
                room.sendMessage(`/nick ${config.botNick}`);
                logger.info(`[collector] Set nick '${config.botNick}' in ${room.target}`);
            }
            logger.info(`[collector] Joined room: ${room.target}`);
        }

        startFlushTimer();
        startSender(sendChatMessage);
        startPresence(setRoomPresence);
    } catch (err) {
        logger.error('[collector] Failed to initialize:', err);
        scheduleReconnect();
    }
}

function scheduleReconnect() {
    if (reconnectTimeout) return;
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

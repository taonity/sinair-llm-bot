import { config } from './config.js';
import { logger } from './logger.js';

let pollTimer = null;
// Last presence value applied per room, so we only push a status change when it actually changes.
const applied = new Map();

/**
 * Polls the backend for the bot's per-room presence (away/back) and reflects any change in chat
 * via the provided callback. Presence is driven by the bot's cooldown/mute state on the backend;
 * the reply debouncer does not affect it.
 *
 * @param {(roomTarget: string, presence: 'back' | 'away') => boolean} setRoomPresence
 *        Applies the presence to a joined room; returns false if the room is not joined yet.
 */
export function startPresence(setRoomPresence) {
    if (!config.botSendEnabled) {
        logger.info('[presence] Bot presence disabled (BOT_SEND_ENABLED=false)');
        return;
    }
    const presenceUrl = `${config.outboundUrl}/presence`;
    logger.info(`[presence] Polling ${presenceUrl} every ${config.presencePollInterval}ms`);

    pollTimer = setInterval(async () => {
        try {
            const response = await fetch(presenceUrl, { method: 'GET' });
            if (!response.ok) {
                logger.error(`[presence] Poll failed ${response.status}: ${await response.text()}`);
                return;
            }
            const presences = await response.json();
            if (!Array.isArray(presences)) return;

            for (const item of presences) {
                const desired = String(item.presence).toLowerCase();
                if (applied.get(item.roomTarget) === desired) continue;
                const delivered = setRoomPresence(item.roomTarget, desired);
                if (delivered) {
                    applied.set(item.roomTarget, desired);
                    logger.info(`[presence] ${item.roomTarget} -> ${desired}`);
                }
            }
        } catch (err) {
            logger.error('[presence] Poll loop error:', err.message);
        }
    }, config.presencePollInterval);
}

export function stopPresence() {
    if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
    }
    // Force a fresh re-apply after a reconnect (the room status resets on rejoin).
    applied.clear();
}

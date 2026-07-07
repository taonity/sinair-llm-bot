import { config } from './config.js';
import { logger } from './logger.js';

let pollTimer = null;
// Last presence/nick value applied per room, so we only push a change when it actually changes.
const appliedStatus = new Map();
const appliedNick = new Map();

/**
 * Polls the backend for the bot's per-room presence (away/back) and nick, applying any change via
 * the callbacks. While a room is asleep (`!sleep`) the backend reports a `nickSuffix` so the bot is
 * renamed (e.g. `segfault-zzz`).
 */
export function startPresence(setRoomPresence, setRoomNick) {
    if (!config.botSendEnabled) {
        logger.debug('[presence] Bot presence disabled (BOT_SEND_ENABLED=false)');
        return;
    }
    const presenceUrl = `${config.outboundUrl}/presence`;
    logger.debug(`[presence] Polling ${presenceUrl} every ${config.presencePollInterval}ms`);

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
                if (appliedStatus.get(item.roomTarget) !== desired) {
                    if (setRoomPresence(item.roomTarget, desired)) {
                        appliedStatus.set(item.roomTarget, desired);
                        logger.info(`[presence] ${item.roomTarget} -> ${desired}`);
                    }
                }

                const desiredNick = `${config.botNick}${item.nickSuffix || ''}`;
                if (appliedNick.get(item.roomTarget) !== desiredNick) {
                    if (setRoomNick(item.roomTarget, desiredNick)) {
                        appliedNick.set(item.roomTarget, desiredNick);
                        logger.info(`[presence] ${item.roomTarget} nick -> ${desiredNick}`);
                    }
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
    // Force a fresh re-apply after a reconnect (the room status/nick resets on rejoin).
    appliedStatus.clear();
    appliedNick.clear();
}

import { config } from './config.js';
import { logger } from './logger.js';

let pollTimer = null;
// The server expires typing state, so active rooms are periodically reasserted.
const typingSince = new Map();

export function startTyping(setRoomTyping) {
    if (!config.botSendEnabled) {
        logger.debug('[typing] Bot typing disabled (BOT_SEND_ENABLED=false)');
        return;
    }
    const typingUrl = `${config.outboundUrl}/typing`;
    logger.debug(`[typing] Polling ${typingUrl} every ${config.typingPollInterval}ms`);

    pollTimer = setInterval(async () => {
        try {
            const response = await fetch(typingUrl, { method: 'GET' });
            if (!response.ok) {
                logger.error(`[typing] Poll failed ${response.status}: ${await response.text()}`);
                return;
            }
            const rooms = await response.json();
            if (!Array.isArray(rooms)) return;
            const active = new Set(rooms);

            const now = Date.now();
            for (const target of active) {
                const last = typingSince.get(target);
                const stale = last === undefined || now - last >= config.typingRefreshInterval;
                if (stale && setRoomTyping(target, true)) {
                    if (last === undefined) logger.debug(`[typing] ${target} started typing`);
                    typingSince.set(target, now);
                }
            }
            for (const target of [...typingSince.keys()]) {
                if (!active.has(target)) {
                    setRoomTyping(target, false);
                    typingSince.delete(target);
                    logger.debug(`[typing] ${target} stopped typing`);
                }
            }
        } catch (err) {
            logger.error('[typing] Poll loop error:', err.message);
        }
    }, config.typingPollInterval);
}

export function stopTyping() {
    if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
    }
    typingSince.clear();
}

import { config } from './config.js';
import { logger } from './logger.js';

let pollTimer = null;
// Per active room, the last time we asserted the typing status to the chat server. The chat
// server auto-expires typing after a few seconds, so we re-assert it periodically — but only every
// `typingRefreshInterval`, decoupled from the (faster) poll so we don't spam status packets.
const typingSince = new Map();

/**
 * Polls the backend for the rooms where the bot is composing a reply and reflects that as a chat
 * typing indicator. Polling is fast so start/stop is responsive; the typing status itself is only
 * re-asserted to the chat server every `typingRefreshInterval`, and stop_typing is emitted once a
 * room drops out.
 */
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
    // Room typing status resets on rejoin, so forget what we applied and re-derive after reconnect.
    typingSince.clear();
}

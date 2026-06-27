import { config } from './config.js';
import { logger } from './logger.js';

let pollTimer = null;

/**
 * Polls the backend for queued bot replies, sends them to the chat via the provided
 * `sendChatMessage(target, text)` callback, and acknowledges the ones delivered.
 */
export function startSender(sendChatMessage) {
    if (!config.botSendEnabled) {
        logger.info('[sender] Bot sending disabled (BOT_SEND_ENABLED=false)');
        return;
    }
    const ackUrl = `${config.outboundUrl}/ack`;
    logger.info(`[sender] Polling ${config.outboundUrl} every ${config.outboundPollInterval}ms`);

    pollTimer = setInterval(async () => {
        try {
            const response = await fetch(config.outboundUrl, { method: 'GET' });
            if (!response.ok) {
                logger.error(`[sender] Claim failed ${response.status}: ${await response.text()}`);
                return;
            }
            const pending = await response.json();
            if (!Array.isArray(pending) || pending.length === 0) return;

            const sentIds = [];
            for (const item of pending) {
                const delivered = sendChatMessage(item.roomTarget, item.messageText);
                if (delivered) {
                    sentIds.push(item.id);
                    logger.info(`[sender] Sent reply to ${item.roomTarget}: ${item.messageText.slice(0, 80)}`);
                } else {
                    logger.warn(`[sender] No joined room for ${item.roomTarget}, will retry later`);
                }
            }

            if (sentIds.length > 0) {
                await fetch(ackUrl, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ ids: sentIds }),
                });
            }
        } catch (err) {
            logger.error('[sender] Poll loop error:', err.message);
        }
    }, config.outboundPollInterval);
}

export function stopSender() {
    if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
    }
}

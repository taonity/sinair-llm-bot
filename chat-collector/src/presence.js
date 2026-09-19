import { config } from './config.js';
import { logger } from './logger.js';

let pollTimer = null;
const appliedStatus = new Map();
const appliedNick = new Map();

export async function loadPresences() {
    const response = await fetch(`${config.outboundUrl}/presence`, { method: 'GET' });
    if (!response.ok) {
        throw new Error(`Presence request failed ${response.status}: ${await response.text()}`);
    }
    const presences = await response.json();
    return Array.isArray(presences) ? presences : [];
}

export async function persistNickname(nickname) {
    const response = await fetch(`${config.outboundUrl}/presence/nickname`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nickname }),
    });
    if (!response.ok) {
        throw new Error(`Nickname update failed ${response.status}: ${await response.text()}`);
    }
}

export function startPresence(setRoomPresence, setRoomNick) {
    if (!config.botSendEnabled) {
        logger.debug('[presence] Bot presence disabled (BOT_SEND_ENABLED=false)');
        return;
    }
    const presenceUrl = `${config.outboundUrl}/presence`;
    logger.debug(`[presence] Polling ${presenceUrl} every ${config.presencePollInterval}ms`);

    const poll = async () => {
        try {
            const presences = await loadPresences();

            for (const item of presences) {
                const desired = String(item.presence).toLowerCase();
                if (appliedStatus.get(item.roomTarget) !== desired) {
                    if (setRoomPresence(item.roomTarget, desired)) {
                        appliedStatus.set(item.roomTarget, desired);
                        logger.info(`[presence] ${item.roomTarget} -> ${desired}`);
                    }
                }

                const desiredNick = item.nickname || `${config.botNick}${item.nickSuffix || ''}`;
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
    };

    void poll();
    pollTimer = setInterval(poll, config.presencePollInterval);
}

export function stopPresence() {
    if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
    }
    appliedStatus.clear();
    appliedNick.clear();
}

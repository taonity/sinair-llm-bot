import { config } from './config.js';
import { logger } from './logger.js';

let timer = null;

export async function reportCollectorStatus(rooms) {
    const response = await fetch(`${config.outboundUrl}/collector-status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ rooms, sendingEnabled: config.botSendEnabled }),
        signal: AbortSignal.timeout(config.telemetryTimeoutMs),
    });
    if (!response.ok) throw new Error(`Collector telemetry returned HTTP ${response.status}`);
}

export function startTelemetry(getReadyRooms) {
    stopTelemetry();
    let polling = false;
    const poll = async () => {
        if (polling) return;
        polling = true;
        try {
            await reportCollectorStatus(getReadyRooms());
        } catch (error) {
            logger.warn(`[telemetry] Status update failed: ${error.name}`);
        } finally {
            polling = false;
        }
    };
    void poll();
    timer = setInterval(poll, config.telemetryIntervalMs);
}

export function stopTelemetry() {
    if (timer) clearInterval(timer);
    timer = null;
}
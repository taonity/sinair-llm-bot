import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('./config.js', () => ({
    config: {
        outboundUrl: 'http://backend.test/api/chat/outbound',
        botSendEnabled: false,
        telemetryIntervalMs: 30000,
        telemetryTimeoutMs: 5000,
    },
}));
vi.mock('./logger.js', () => ({ logger: { warn: vi.fn() } }));

import { reportCollectorStatus, startTelemetry, stopTelemetry } from './telemetry.js';

describe('collector telemetry', () => {
    afterEach(() => {
        stopTelemetry();
        vi.useRealTimers();
        vi.unstubAllGlobals();
    });

    it('reports ready rooms even when sending is disabled and bounds HTTP calls', async () => {
        const fetch = vi.fn().mockResolvedValue({ ok: true });
        vi.stubGlobal('fetch', fetch);
        await reportCollectorStatus(['#room']);
        expect(fetch).toHaveBeenCalledWith('http://backend.test/api/chat/outbound/collector-status', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ rooms: ['#room'], sendingEnabled: false }),
            signal: expect.any(AbortSignal),
        });
    });

    it('reports periodically and stops on disconnect', async () => {
        vi.useFakeTimers();
        const fetch = vi.fn().mockResolvedValue({ ok: true });
        vi.stubGlobal('fetch', fetch);
        const rooms = vi.fn().mockReturnValue([]);
        startTelemetry(rooms);
        await vi.advanceTimersByTimeAsync(30000);
        expect(fetch).toHaveBeenCalledTimes(2);
        stopTelemetry();
        await vi.advanceTimersByTimeAsync(60000);
        expect(fetch).toHaveBeenCalledTimes(2);
    });

    it('does not consume provider error bodies', async () => {
        const text = vi.fn();
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503, text }));
        await expect(reportCollectorStatus([])).rejects.toThrow('HTTP 503');
        expect(text).not.toHaveBeenCalled();
    });
});
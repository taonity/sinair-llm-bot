import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('./config.js', () => ({
    config: {
        botSendEnabled: true,
        botNick: 'configured-nick',
        outboundUrl: 'http://backend.test/outbound',
        presencePollInterval: 5000,
    },
}));

vi.mock('./logger.js', () => ({
    logger: {
        debug: vi.fn(),
        info: vi.fn(),
        error: vi.fn(),
    },
}));

import { startPresence, stopPresence } from './presence.js';

describe('presence nickname synchronization', () => {
    afterEach(() => {
        stopPresence();
        vi.unstubAllGlobals();
    });

    it('reapplies the database-backed nickname after reconnect', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: true,
            json: async () => [{ roomTarget: '#room', presence: 'BACK', nickname: 'database-nick' }],
        }));
        const setRoomPresence = vi.fn(() => true);
        const setRoomNick = vi.fn(() => true);

        startPresence(setRoomPresence, setRoomNick);
        await vi.waitFor(() => expect(setRoomNick).toHaveBeenCalledTimes(1));

        stopPresence();
        startPresence(setRoomPresence, setRoomNick);
        await vi.waitFor(() => expect(setRoomNick).toHaveBeenCalledTimes(2));

        expect(setRoomNick).toHaveBeenLastCalledWith('#room', 'database-nick');
    });
});
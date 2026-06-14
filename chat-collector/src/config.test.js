import { describe, it, expect, vi, beforeEach } from 'vitest';

describe('config', () => {
    beforeEach(() => {
        vi.resetModules();
    });

    it('should load all required env vars', async () => {
        vi.stubEnv('CHAT_WS_URL', 'ws://localhost:3001/ws/chat');
        vi.stubEnv('CHAT_API_KEY', 'test-key');
        vi.stubEnv('CHAT_ROOMS', '#test,#dev');
        vi.stubEnv('BACKEND_URL', 'http://localhost:8080/api/chat/ingest');
        vi.stubEnv('BATCH_SIZE', '5');
        vi.stubEnv('BATCH_FLUSH_INTERVAL', '3000');

        const { config } = await import('./config.js');

        expect(config.chatWsUrl).toBe('ws://localhost:3001/ws/chat');
        expect(config.chatApiKey).toBe('test-key');
        expect(config.chatRooms).toEqual(['#test', '#dev']);
        expect(config.backendUrl).toBe('http://localhost:8080/api/chat/ingest');
        expect(config.batchSize).toBe(5);
        expect(config.batchFlushInterval).toBe(3000);
    });

    it('should parse single room correctly', async () => {
        vi.stubEnv('CHAT_WS_URL', 'wss://sinair.net/ws/chat');
        vi.stubEnv('CHAT_API_KEY', 'key');
        vi.stubEnv('CHAT_ROOMS', '#chat');
        vi.stubEnv('BACKEND_URL', 'http://backend:8080/api/chat/ingest');
        vi.stubEnv('BATCH_SIZE', '10');
        vi.stubEnv('BATCH_FLUSH_INTERVAL', '5000');

        const { config } = await import('./config.js');

        expect(config.chatRooms).toEqual(['#chat']);
    });

    it('should exit if required env var is missing', async () => {
        const mockExit = vi.spyOn(process, 'exit').mockImplementation(() => { throw new Error('exit'); });
        // Unset ENV_FILE so it doesn't load .env.local
        vi.stubEnv('ENV_FILE', '.env.nonexistent');
        vi.stubEnv('CHAT_WS_URL', 'ws://localhost:3001/ws/chat');
        delete process.env.CHAT_API_KEY;
        vi.stubEnv('CHAT_ROOMS', '#test');
        vi.stubEnv('BACKEND_URL', 'http://localhost:8080/api/chat/ingest');
        vi.stubEnv('BATCH_SIZE', '10');
        vi.stubEnv('BATCH_FLUSH_INTERVAL', '5000');

        await expect(import('./config.js')).rejects.toThrow('exit');
        expect(mockExit).toHaveBeenCalledWith(1);

        mockExit.mockRestore();
    });
});

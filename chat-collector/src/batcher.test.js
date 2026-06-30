import { describe, it, expect, beforeEach, vi } from 'vitest';

vi.mock('./config.js', () => ({
    config: {
        backendUrl: 'http://localhost:8080/api/chat/ingest',
        batchSize: 3,
        batchFlushInterval: 5000,
    },
}));

import { bufferMessage, bufferEvent, startFlushTimer, stopFlushTimer, resetBuffers } from './batcher.js';

describe('batcher', () => {
    beforeEach(() => {
        vi.restoreAllMocks();
        stopFlushTimer();
        resetBuffers();
    });

    describe('bufferMessage', () => {
        it('should buffer messages and flush when batch size is reached', async () => {
            const mockResponse = {
                ok: true,
                json: () => Promise.resolve({ messagesStored: 3, messagesDuplicate: 0, eventsStored: 0, eventsDuplicate: 0 }),
            };
            global.fetch = vi.fn().mockResolvedValue(mockResponse);

            bufferMessage({ externalId: '1', roomTarget: '#test', senderMemberId: 1, senderLogin: 'Alice', messageText: 'hi', messageStyle: 'message', sentAt: 1000 });
            bufferMessage({ externalId: '2', roomTarget: '#test', senderMemberId: 2, senderLogin: 'Bob', messageText: 'hey', messageStyle: 'message', sentAt: 1001 });

            expect(global.fetch).not.toHaveBeenCalled();

            bufferMessage({ externalId: '3', roomTarget: '#test', senderMemberId: 1, senderLogin: 'Alice', messageText: 'sup', messageStyle: 'message', sentAt: 1002 });

            await vi.waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));

            const [url, options] = global.fetch.mock.calls[0];
            expect(url).toBe('http://localhost:8080/api/chat/ingest');
            expect(options.method).toBe('POST');

            const body = JSON.parse(options.body);
            expect(body.messages).toHaveLength(3);
            expect(body.events).toHaveLength(0);
        });

        it('should re-buffer messages on fetch failure', async () => {
            global.fetch = vi.fn().mockRejectedValue(new Error('Connection refused'));

            bufferMessage({ externalId: '1', roomTarget: '#test', senderMemberId: 1, senderLogin: 'Alice', messageText: 'hi', messageStyle: 'message', sentAt: 1000 });
            bufferMessage({ externalId: '2', roomTarget: '#test', senderMemberId: 2, senderLogin: 'Bob', messageText: 'hey', messageStyle: 'message', sentAt: 1001 });
            bufferMessage({ externalId: '3', roomTarget: '#test', senderMemberId: 1, senderLogin: 'Alice', messageText: 'sup', messageStyle: 'message', sentAt: 1002 });

            await vi.waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));

            bufferMessage({ externalId: '4', roomTarget: '#test', senderMemberId: 1, senderLogin: 'Alice', messageText: 'hi again', messageStyle: 'message', sentAt: 1003 });
        });
    });

    describe('bufferEvent', () => {
        it('should buffer events and flush when batch size is reached', async () => {
            const mockResponse = {
                ok: true,
                json: () => Promise.resolve({ messagesStored: 0, messagesDuplicate: 0, eventsStored: 3, eventsDuplicate: 0 }),
            };
            global.fetch = vi.fn().mockResolvedValue(mockResponse);

            bufferEvent({ roomTarget: '#test', memberId: 1, status: 'online', memberName: 'Alice', eventTime: 1000 });
            bufferEvent({ roomTarget: '#test', memberId: 2, status: 'away', memberName: 'Bob', eventTime: 1001 });
            bufferEvent({ roomTarget: '#test', memberId: 1, status: 'offline', memberName: 'Alice', eventTime: 1002 });

            await vi.waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));

            const body = JSON.parse(global.fetch.mock.calls[0][1].body);
            expect(body.events).toHaveLength(3);
            expect(body.events[0].memberName).toBe('Alice');
            expect(body.events[1].status).toBe('away');
        });
    });

    describe('bufferMessage with non-ok response', () => {
        it('should re-buffer on HTTP error response', async () => {
            const mockResponse = {
                ok: false,
                status: 500,
                text: () => Promise.resolve('Internal Server Error'),
            };
            global.fetch = vi.fn().mockResolvedValue(mockResponse);

            bufferMessage({ externalId: '1', roomTarget: '#test', senderMemberId: 1, senderLogin: 'Alice', messageText: 'hi', messageStyle: 'message', sentAt: 1000 });
            bufferMessage({ externalId: '2', roomTarget: '#test', senderMemberId: 2, senderLogin: 'Bob', messageText: 'hey', messageStyle: 'message', sentAt: 1001 });
            bufferMessage({ externalId: '3', roomTarget: '#test', senderMemberId: 1, senderLogin: 'Alice', messageText: 'sup', messageStyle: 'message', sentAt: 1002 });

            await vi.waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));
        });
    });
});

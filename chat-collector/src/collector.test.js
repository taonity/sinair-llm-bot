import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => {
    const clients = [];
    const roomMembers = [];
    const joinedRooms = [];
    const loadPresences = vi.fn(async () => []);
    const persistNickname = vi.fn(async () => {});
    let failFirstOpen = true;

    class MockWsChat {
        constructor() {
            this.handlers = new Map();
            this.connected = false;
            this.joinedTargets = [];
            this.joinOptions = [];
            this.i = {
                OPEN: 1,
                readyState: 1,
                on: vi.fn(),
                ping: vi.fn(),
                terminate: vi.fn(),
            };
            clients.push(this);
        }

        on(event, handler) {
            this.handlers.set(event, handler);
        }

        emit(event, ...args) {
            this.handlers.get(event)?.(...args);
        }

        async open() {
            if (failFirstOpen && clients.indexOf(this) === 0) throw new Error('server unavailable');
            this.connected = true;
            this.emit('open');
        }

        async authByApiKey() {
            return { user_id: 42, token: 'new-token' };
        }

        async joinRoom(target, options) {
            this.joinedTargets.push(target);
            this.joinOptions.push(options);
            const room = {
                target,
                memberId: 10,
                memberNick: 'anonymous',
                members: [...roomMembers],
                sendMessage: vi.fn(),
                changeStatus: vi.fn(),
            };
            joinedRooms.push(room);
            return room;
        }

        async close() {
            this.connected = false;
        }
    }

    return {
        clients,
        roomMembers,
        joinedRooms,
        loadPresences,
        persistNickname,
        setFailFirstOpen(value) {
            failFirstOpen = value;
        },
        MockWsChat,
        logger: {
            debug: vi.fn(),
            info: vi.fn(),
            warn: vi.fn(),
            error: vi.fn(),
        },
    };
});

vi.mock('@iassasin/wschatapi', () => ({
    WsChat: mocks.MockWsChat,
    WsChatEvents: {
        open: 'open',
        close: 'close',
        connectionError: 'connectionError',
        error: 'error',
        joinRoom: 'joinRoom',
        message: 'message',
        sysMessage: 'sysMessage',
        userStatusChange: 'userStatusChange',
    },
    UserStatus: {},
    MessageStyle: {},
}));

vi.mock('fs', () => ({
    readFileSync: vi.fn(() => { throw new Error('missing'); }),
    writeFileSync: vi.fn(),
    rmSync: vi.fn(),
}));

vi.mock('./config.js', () => ({
    config: {
        chatWsUrl: 'ws://chat.test',
        chatApiKey: 'api-key',
        chatRooms: ['#room'],
        tokenFile: '/tmp/session-token',
        restoreRejoinGrace: 0,
        historyWarmupIdleMs: 1500,
        historyWarmupMaxMs: 10000,
        messageLiveMaxAgeMs: 60000,
        heartbeatIntervalMs: 25000,
        heartbeatTimeoutMs: 60000,
        shutdownCloseTimeout: 100,
        botColor: null,
        botNick: null,
    },
}));

vi.mock('./logger.js', () => ({ logger: mocks.logger }));
vi.mock('./batcher.js', () => ({
    bufferMessage: vi.fn(),
    bufferEvent: vi.fn(),
    startFlushTimer: vi.fn(),
    stopFlushTimer: vi.fn(),
}));
vi.mock('./sender.js', () => ({ startSender: vi.fn(), stopSender: vi.fn() }));
vi.mock('./presence.js', () => ({
    loadPresences: mocks.loadPresences,
    persistNickname: mocks.persistNickname,
    startPresence: vi.fn(),
    stopPresence: vi.fn(),
}));
vi.mock('./typing.js', () => ({ startTyping: vi.fn(), stopTyping: vi.fn() }));

describe('collector reconnect lifecycle', () => {
    beforeEach(() => {
        vi.resetModules();
        vi.useFakeTimers();
        vi.clearAllMocks();
        mocks.clients.length = 0;
        mocks.roomMembers.length = 0;
        mocks.joinedRooms.length = 0;
        mocks.setFailFirstOpen(true);
        mocks.loadPresences.mockResolvedValue([]);
    });

    afterEach(() => {
        vi.clearAllTimers();
        vi.useRealTimers();
    });

    it('survives stale close events and heartbeat timeouts without a close event', async () => {
        const { startCollector } = await import('./collector.js');

        await startCollector();
        expect(mocks.clients).toHaveLength(1);
        expect(mocks.logger.error).toHaveBeenCalledWith(
            '[collector] Failed while opening connection; joinedRooms=none: Error: server unavailable',
        );
        expect(mocks.logger.error.mock.calls[0]).toHaveLength(1);
        expect(mocks.logger.debug).toHaveBeenCalledWith(
            '[collector] Failed while opening connection; joinedRooms=none details:',
            expect.any(Error),
        );

        await vi.advanceTimersByTimeAsync(10000);
        expect(mocks.clients).toHaveLength(2);
        expect(mocks.clients[1].i.on).toHaveBeenCalledWith('pong', expect.any(Function));
        expect(mocks.logger.info).toHaveBeenCalledWith(expect.stringContaining('Heartbeat monitoring started'));

        mocks.clients[0].emit('close');
        await vi.advanceTimersByTimeAsync(10000);

        expect(mocks.clients).toHaveLength(2);
        expect(mocks.logger.debug).toHaveBeenCalledWith(
            '[collector] Ignoring close event from a superseded connection',
        );

        await vi.advanceTimersByTimeAsync(65000);
        expect(mocks.clients[1].i.terminate).toHaveBeenCalledOnce();
        expect(mocks.logger.warn).toHaveBeenCalledWith(expect.stringContaining('Heartbeat timeout'));

        await vi.advanceTimersByTimeAsync(10000);
        expect(mocks.clients).toHaveLength(3);
        expect(mocks.clients[2].joinedTargets).toEqual(['#room']);
    });

    it('joins without the saved nick and persists an available suffix when it is occupied', async () => {
        mocks.setFailFirstOpen(false);
        mocks.roomMembers.push(
            { member_id: 20, name: 'database-nick' },
            { member_id: 21, name: 'database-nick_2' },
        );
        mocks.loadPresences.mockResolvedValue([
            { roomTarget: '#room', nickname: 'database-nick', nickSuffix: '' },
        ]);
        const { startCollector } = await import('./collector.js');

        await startCollector();

        expect(mocks.clients[0].joinOptions).toEqual([{ autoLogin: false, loadHistory: true }]);
        expect(mocks.persistNickname).toHaveBeenCalledWith('database-nick_3');
        expect(mocks.joinedRooms[0].sendMessage).toHaveBeenCalledWith('/nick database-nick_3');
    });
});
import { readFileSync, existsSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import { WebSocketServer } from 'ws';
import { PacketType, UserStatus, MessageStyle } from './protocol.js';
import {
    generateHistoryMessages,
    generateOnlineList,
    generateRandomMessage,
    generateStatusEvent,
    FAKE_USERS,
} from './generators.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const envPath = resolve(__dirname, '..', '.env.local');
if (existsSync(envPath)) {
    const lines = readFileSync(envPath, 'utf-8').split('\n');
    for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) continue;
        const eqIndex = trimmed.indexOf('=');
        if (eqIndex === -1) continue;
        const key = trimmed.slice(0, eqIndex).trim();
        const value = trimmed.slice(eqIndex + 1).trim();
        if (!process.env[key]) {
            process.env[key] = value;
        }
    }
}

const PORT = parseInt(process.env.CHAT_STUBS_PORT || '3001', 10);
const MESSAGE_INTERVAL = parseInt(process.env.CHAT_STUBS_MESSAGE_INTERVAL || '16000', 10);
const EVENT_INTERVAL = parseInt(process.env.CHAT_STUBS_EVENT_INTERVAL || '30000', 10);
// Set CHAT_STUBS_AUTO_TRAFFIC=false for deterministic runs (only client-sent messages flow).
const AUTO_TRAFFIC = (process.env.CHAT_STUBS_AUTO_TRAFFIC || 'true').toLowerCase() !== 'false';

const wss = new WebSocketServer({ port: PORT, path: '/ws/chat' });

const connectedClients = new Set();
const clientRooms = new WeakMap(); // ws -> Set<string>
const clientIdentity = new WeakMap(); // ws -> { login, memberId }
let memberIdSeq = 10;

console.log(`[chat-stubs] WebSocket stub server listening on ws://0.0.0.0:${PORT}/ws/chat`);

wss.on('connection', (ws) => {
    console.log('[chat-stubs] Client connected');
    connectedClients.add(ws);
    clientRooms.set(ws, new Set());
    clientIdentity.set(ws, { login: 'collector', memberId: memberIdSeq++ });

    ws.on('message', (data) => {
        let packet;
        try {
            packet = JSON.parse(data.toString());
        } catch {
            console.error('[chat-stubs] Invalid JSON received');
            return;
        }

        handlePacket(ws, packet);
    });

    ws.on('close', () => {
        console.log('[chat-stubs] Client disconnected');
        connectedClients.delete(ws);
    });
});

function send(ws, packet) {
    if (ws.readyState === ws.OPEN) {
        ws.send(JSON.stringify(packet));
    }
}

function handlePacket(ws, packet) {
    switch (packet.type) {
        case PacketType.auth:
            handleAuth(ws, packet);
            break;

        case PacketType.join:
            handleJoin(ws, packet);
            break;

        case PacketType.leave:
            handleLeave(ws, packet);
            break;

        case PacketType.message:
            // Client sending a message — echo it back as if delivered
            handleClientMessage(ws, packet);
            break;

        case PacketType.ping:
            send(ws, { type: PacketType.ping });
            break;

        case PacketType.status:
            // Acknowledge status changes silently
            break;

        default:
            console.log(`[chat-stubs] Unhandled packet type: ${packet.type}`);
    }
}

function handleAuth(ws, packet) {
    const botMemberId = 10;
    console.log(`[chat-stubs] Auth request (api_key=${packet.api_key ? '***' : 'none'})`);

    send(ws, {
        type: PacketType.auth,
        sequenceId: packet.sequenceId,
        user_id: 200,
        name: 'TestBot',
        token: 'stub-restore-token',
    });
}

function handleJoin(ws, packet) {
    const target = packet.target;
    const rooms = clientRooms.get(ws);
    rooms.add(target);

    const botMemberId = 10;

    console.log(`[chat-stubs] Join room: ${target} (loadHistory=${packet.load_history})`);

    // Send join confirmation
    send(ws, {
        type: PacketType.join,
        sequenceId: packet.sequenceId,
        target,
        member_id: botMemberId,
        login: 'TestBot',
    });

    // Send online list (resolves the join promise in the client)
    send(ws, {
        type: PacketType.online_list,
        sequenceId: packet.sequenceId,
        target,
        list: generateOnlineList(target),
    });

    // Send history messages if requested
    if (packet.load_history) {
        const history = generateHistoryMessages(target, 50);
        for (const msg of history) {
            send(ws, msg);
        }
        console.log(`[chat-stubs] Sent ${history.length} history messages for ${target}`);
    }
}

function handleLeave(ws, packet) {
    const rooms = clientRooms.get(ws);
    rooms.delete(packet.target);

    send(ws, {
        type: PacketType.leave,
        sequenceId: packet.sequenceId,
        target: packet.target,
    });
}

function handleClientMessage(ws, packet) {
    const identity = clientIdentity.get(ws) || { login: 'anon', memberId: 10 };
    const text = packet.message || '';

    // Treat "/nick X" as a nick change, not a chat message (the bot sets its nick this way).
    const nickMatch = text.match(/^\/nick\s+(.+)$/);
    if (nickMatch) {
        identity.login = nickMatch[1].trim();
        clientIdentity.set(ws, identity);
        console.log(`[chat-stubs] Client set nick: ${identity.login}`);
        return;
    }

    // Broadcast to every client present in the room, attributed to the sender's nick.
    const outPacket = {
        type: PacketType.message,
        id: String(Date.now()),
        color: '#cccccc',
        from: identity.memberId,
        from_login: identity.login,
        message: text,
        style: MessageStyle.message,
        target: packet.target,
        time: Math.floor(Date.now() / 1000),
        to: 0,
    };
    for (const client of connectedClients) {
        const rooms = clientRooms.get(client);
        if (rooms && rooms.has(packet.target)) {
            send(client, outPacket);
        }
    }
}

// Periodically send fake messages to all connected clients
setInterval(() => {
    if (!AUTO_TRAFFIC) return;
    for (const ws of connectedClients) {
        const rooms = clientRooms.get(ws);
        if (!rooms || rooms.size === 0) continue;

        for (const room of rooms) {
            const msg = generateRandomMessage(room);
            send(ws, msg);
        }
    }
}, MESSAGE_INTERVAL);

// Periodically send status events
setInterval(() => {
    if (!AUTO_TRAFFIC) return;
    for (const ws of connectedClients) {
        const rooms = clientRooms.get(ws);
        if (!rooms || rooms.size === 0) continue;

        for (const room of rooms) {
            const event = generateStatusEvent(room);
            send(ws, event);
        }
    }
}, EVENT_INTERVAL);

import { readFileSync, existsSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const envFile = process.env.ENV_FILE || '.env';
const envPath = resolve(__dirname, '..', envFile);

function loadEnv() {
    if (!existsSync(envPath)) {
        // In Docker, env vars are injected directly — .env file is not required.
        return;
    }
    console.log(`[config] Loading env from: ${envFile}`);
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

loadEnv();

// Default location for the persisted session token when TOKEN_FILE isn't set explicitly.
// Docker sets TOKEN_FILE to a mounted volume path; for local launches (Windows/macOS/Linux) we
// resolve a writable, absolute path inside the collector folder, namespaced by the env profile
// so the stub (dev:local) and prod (dev:prod) sessions don't clobber each other's token.
const envProfile = envFile.replace(/^\.env\.?/, '') || 'default';
const defaultTokenFile = resolve(__dirname, '..', `.session-token-${envProfile}`);

export const config = {
    chatWsUrl: requireEnv('CHAT_WS_URL'),
    chatApiKey: requireEnv('CHAT_API_KEY'),
    chatRooms: requireEnv('CHAT_ROOMS').split(',').map(r => r.trim()),
    backendUrl: requireEnv('BACKEND_URL'),
    batchSize: parseInt(requireEnv('BATCH_SIZE'), 10),
    batchFlushInterval: parseInt(requireEnv('BATCH_FLUSH_INTERVAL'), 10),
    logLevel: (process.env.LOG_LEVEL || 'info').toLowerCase(),
    // Bot sending: when enabled, the collector polls the backend for queued replies and sends them.
    botSendEnabled: (process.env.BOT_SEND_ENABLED || 'false').toLowerCase() === 'true',
    botNick: process.env.BOT_NICK || 'segfault',
    botColor: '#cc3333',
    outboundUrl: process.env.OUTBOUND_URL || requireEnv('BACKEND_URL').replace(/\/ingest$/, '/outbound'),
    outboundPollInterval: parseInt(process.env.OUTBOUND_POLL_INTERVAL || '3000', 10),
    presencePollInterval: parseInt(process.env.PRESENCE_POLL_INTERVAL || '5000', 10),
    // Typing indicator poll: faster than presence so the "bot is typing" state (shown while it
    // composes a reply) appears promptly and clears quickly once the reply is sent.
    typingPollInterval: parseInt(process.env.TYPING_POLL_INTERVAL || '500', 10),
    // How often the typing status is re-asserted to the chat server while a room stays active
    // (the server auto-expires typing). Decoupled from the poll so fast polling doesn't spam
    // status packets upstream.
    typingRefreshInterval: parseInt(process.env.TYPING_REFRESH_INTERVAL || '3000', 10),
    // After (re)joining a room the server replays a burst of history messages as ordinary
    // messages. They are tagged `historical` for the warm-up window below so the backend stores
    // them but does not re-run commands or react to them. The window ends after idle quiet, capped.
    historyWarmupIdleMs: parseInt(process.env.HISTORY_WARMUP_IDLE_MS || '1500', 10),
    historyWarmupMaxMs: parseInt(process.env.HISTORY_WARMUP_MAX_MS || '10000', 10),
    // Grace period after restoreConnection() for the server's auto-rejoin (joinRoom) events
    // to arrive before we manually join any rooms that weren't restored.
    restoreRejoinGrace: parseInt(process.env.RESTORE_REJOIN_GRACE || '2000', 10),
    // Application-level heartbeat. WebSocket drops (NAT/idle timeouts, half-open TCP, the remote
    // chat server going away) don't always deliver a close frame, so without an active liveness
    // probe the collector can sit on a dead socket forever and never reconnect. We ping the socket
    // every heartbeatIntervalMs and, if no pong (or any traffic) is seen within heartbeatTimeoutMs,
    // treat the connection as dead and force a reconnect.
    heartbeatIntervalMs: parseInt(process.env.HEARTBEAT_INTERVAL_MS || '25000', 10),
    heartbeatTimeoutMs: parseInt(process.env.HEARTBEAT_TIMEOUT_MS || '60000', 10),
    // File where the session token is persisted so an ungraceful restart (SIGKILL/crash) can
    // reclaim the server-side "orphan" session instead of waiting out its timeout. In Docker this
    // is a mounted volume path; locally it defaults to a profile-namespaced file in the collector
    // folder. Best-effort (ignored) if the path isn't writable.
    tokenFile: process.env.TOKEN_FILE || defaultTokenFile,
    // Max time to wait for the WebSocket close handshake during graceful shutdown, so an
    // unresponsive socket can't block past the orchestrator's stop grace period.
    shutdownCloseTimeout: parseInt(process.env.SHUTDOWN_CLOSE_TIMEOUT || '3000', 10),
};

function requireEnv(name) {
    const value = process.env[name];
    if (!value) {
        console.error(`ERROR: ${name} environment variable is required.`);
        console.error(`HINT: Run with a profile: npm run dev:local (stubs) or npm run dev:prod (real server)`);
        process.exit(1);
    }
    return value;
}

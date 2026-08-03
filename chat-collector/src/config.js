import { readFileSync, existsSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const envFile = process.env.ENV_FILE || '.env';
const envPath = resolve(__dirname, '..', envFile);

function loadEnv() {
    if (!existsSync(envPath)) {
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

const envProfile = envFile.replace(/^\.env\.?/, '') || 'default';
// Profile names prevent local and production sessions from sharing a token.
const defaultTokenFile = resolve(__dirname, '..', `.session-token-${envProfile}`);

export const config = {
    chatWsUrl: requireEnv('CHAT_WS_URL'),
    chatApiKey: requireEnv('CHAT_API_KEY'),
    chatRooms: requireEnv('CHAT_ROOMS').split(',').map(r => `#${r.trim()}`),
    backendUrl: requireEnv('BACKEND_URL'),
    batchSize: parseInt(requireEnv('BATCH_SIZE'), 10),
    batchFlushInterval: parseInt(requireEnv('BATCH_FLUSH_INTERVAL'), 10),
    logLevel: (process.env.LOG_LEVEL || 'info').toLowerCase(),
    botSendEnabled: (process.env.BOT_SEND_ENABLED || 'false').toLowerCase() === 'true',
    botNick: process.env.BOT_NICK || 'segfault',
    botColor: '#cc3333',
    outboundUrl: process.env.OUTBOUND_URL || requireEnv('BACKEND_URL').replace(/\/ingest$/, '/outbound'),
    outboundPollInterval: parseInt(process.env.OUTBOUND_POLL_INTERVAL || '3000', 10),
    presencePollInterval: parseInt(process.env.PRESENCE_POLL_INTERVAL || '5000', 10),
    typingPollInterval: parseInt(process.env.TYPING_POLL_INTERVAL || '500', 10),
    typingRefreshInterval: parseInt(process.env.TYPING_REFRESH_INTERVAL || '3000', 10),
    historyWarmupIdleMs: parseInt(process.env.HISTORY_WARMUP_IDLE_MS || '1500', 10),
    historyWarmupMaxMs: parseInt(process.env.HISTORY_WARMUP_MAX_MS || '10000', 10),
    restoreRejoinGrace: parseInt(process.env.RESTORE_REJOIN_GRACE || '2000', 10),
    heartbeatIntervalMs: parseInt(process.env.HEARTBEAT_INTERVAL_MS || '25000', 10),
    heartbeatTimeoutMs: parseInt(process.env.HEARTBEAT_TIMEOUT_MS || '60000', 10),
    tokenFile: process.env.TOKEN_FILE || defaultTokenFile,
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

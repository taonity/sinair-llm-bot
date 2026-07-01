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

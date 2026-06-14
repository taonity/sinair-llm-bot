# Chat Collector

WebSocket collector for sinair.net chat. Connects to the chat server, records messages and events, and forwards them to the backend for storage.

## Profiles

The collector supports two profiles via env files:

| Profile | Env file | Chat server | Use case |
|---------|----------|-------------|----------|
| `local` | `.env.local` | `ws://localhost:3001/ws/chat` (chat-stubs) | Local dev with fake data |
| `prod` | `.env.prod` | `wss://sinair.net/ws/chat` | Real chat server |

## Quick start

```bash
npm install

# Run with stubs (start chat-stubs first)
npm run dev:local

# Run with real server (edit .env.prod with your API key first)
npm run dev:prod
```

## Setup for real server

1. Get an API key from your sinair.net profile
2. Edit `.env.prod` and set `CHAT_API_KEY=<your-key>`
3. Set `CHAT_ROOMS` to the room(s) you want to collect from
4. Run `npm run dev:prod`

## Running with chat-stubs

1. Start the stub server: `cd ../chat-stubs && npm start`
2. Start the collector: `npm run dev:local`

The stub server emits fake messages every 8s and status events every 15s.

## Docker

In production Docker Compose, the collector reads env vars from the compose `.env` file.
The `CHAT_WS_URL` should point to the real server (`wss://sinair.net/ws/chat`).

For local Docker development with stubs, use the override file:

```bash
cd templates/docker
docker compose -f docker-compose.yml -f docker-compose.local.yml up
```

## How it works

- Connects to the chat WebSocket server and authenticates with an API key
- Joins configured rooms with `loadHistory: true` (receives ~100 recent messages)
- Buffers incoming messages and events, flushes to backend in batches
- Deduplication is handled server-side (backend rejects duplicates by dedup_key)
- Auto-reconnects on disconnect with a 10s delay
- Filters out `typing`/`stop_typing` events (no training value)

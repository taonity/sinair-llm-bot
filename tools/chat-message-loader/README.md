# chat-message-loader

Small CLI to load and clear `chat_message` rows for testing. Generates a base of
**500 messages** written by **6 made-up users**, spread over the **last 10 days**
(ending today), in the same casual style as `data/history.csv` (reply-quotes,
emoticons, occasional links and `code`). No pipeline or summary rows are produced.

Override the window start with `CHAT_LOADER_START_DATE=YYYY-MM-DD` if you need a
fixed date range instead of the rolling last-10-days default.

## Setup

```bash
cd tools/chat-message-loader
npm install
```

Postgres must be reachable. When the stack runs with the local override the port
is published on `127.0.0.1:5432`:

```bash
cd templates/docker
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d db
```

## Connection

Defaults match `templates/docker/.env`
(`localhost:5432`, user `d` / password `d`, db `sinair_llm_bot_db`).
Override with standard `PG*` env vars or a single `DATABASE_URL`:

```bash
# PowerShell
$env:DATABASE_URL = "postgres://d:d@localhost:5432/sinair_llm_bot_db"
```

## Usage

```bash
node loader.js clear          # wipe chat_message (+ dependent pipeline_run rows)
node loader.js load           # insert all 500 messages
node loader.js load 100       # insert the first 100 messages
node loader.js reload 250     # clear, then insert 250
node loader.js count          # print current chat_message row count
```

Or via npm scripts: `npm run load`, `npm run clear`, `npm run count`,
`npm run reload -- 100`.

Notes:
- `clear` deletes `chat_message` and all `pipeline_run` rows (which reference
  messages by id) in one transaction, so no traces are left dangling.
- `load` uses `ON CONFLICT (dedup_key) DO NOTHING`, so re-running is safe and
  skips already-present rows.
- The dataset is deterministic (fixed texts/timestamps); only row `id`s differ
  between regenerations.

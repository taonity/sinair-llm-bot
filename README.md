# Sinair LLM Bot

An LLM-powered chat bot that lives in a sinair.net group chat as an ordinary member. It reads the
conversation, decides on its own when it has something worth saying, and replies in the persona of a
regular chat participant — witty, opinionated, broadly knowledgeable, and deliberately not spammy.

The project also ships a **data console**: a web dashboard (Google login) for operators to inspect
collected chat data, edit room summaries, and manage who has access.

[Jump to Running locally](#backend)

## Using the bot in chat

The bot joins as a normal member (persona nick **`segfault`**). Here is what a chat user should know.

### When it replies

- It does **not** answer every message. A free heuristic filter drops obvious noise, a cheap LLM
   "gate" call judges ambiguous cases, and only then does it generate a reply.
- **Address it directly** by nick or an alias — `segfault`, `сега`, `сегвульт` (whole-word; `@nick`
   works too) — and it will almost always answer.
- It also reacts to **indirect** prompts: direct follow-ups or replies to its own last message, and
   questions it is clearly and specifically expected to answer.
- It will speak up to **correct genuine misinformation** — a clear, checkable factual falsehood that
   could mislead people (facts only, not opinions, jokes or debatable takes).
- It does **not** chime into threads spontaneously — it stays quiet unless it is actually addressed.
   (A `spontaneous-probability` knob exists but is set to `0`.)
- It writes in **Russian**, matching the chat's casual, lowercase, slang-heavy style — usually one
   or two short sentences, longer only when genuinely explaining something.

### Link & image awareness

- If your message contains a **URL**, the bot fetches and cleans that source before answering and
   grounds its reply in it — GitHub repo READMEs, readable web-page text, or a directly linked image
   (which it can actually look at). Private/internal addresses are blocked and downloads are size-capped.
- For **time-sensitive questions** (latest versions, current events, prices, "newest" anything) it
   can run a live web search so its answer isn't stale.

### Pace & anti-spam

- **Debounce** — after a burst of messages it waits for the conversation to settle (~6s) and then
   judges the whole burst once, instead of replying line by line.
- **Cooldown** — at least **30s** between replies in a room.
- **Rate limit** — at most **8 replies per 20-minute window** per room.
- **Presence** — the bot shows itself as *back* only when it's actually ready to reply (enabled,
   awake, not muted, off cooldown); otherwise it appears *away*.

### Commands

All commands are per-room and can be used by anyone in that room.

| Command  | Effect                                                                                          |
|----------|-------------------------------------------------------------------------------------------------|
| `!stop`  | **Mute** the bot in this room — it keeps reading and storing messages but won't reply.          |
| `!start` | **Unmute** the bot in this room.                                                                |
| `!sleep` | Put the bot **to sleep** in this room — it ignores and drops the room's messages entirely, appears *away*, and renames itself with a `-zzz` suffix (`segfault-zzz`). Stronger than `!stop`. |
| `!wake`  | **Wake** the bot back up in this room.                                                          |

### Data retention

- Collected chat messages, events, outbound messages and ignored-message tombstones are **kept for 7
   days**, then automatically purged by a nightly job. Room summaries are refreshed as data ages out.

## Data console (operator UI)

A Next.js dashboard for operators. Sign in with **Google**; the login page still renders even when
the backend is temporarily unreachable.

### Tabs

- **Messages** — collected chat messages (time, sender, text) with search, filtering, sorting and
   pagination.
- **Events** — chat events (joins, leaves, status changes) with the same controls.
- **Outbound** — messages the bot queued/sent, with status and timestamps.
- **Summaries** — per-room conversation summaries, editable in place (with message count and last
   update time).
- **Admin** — access requests, user management, and the audit log (admins/owners only).

### Roles & access

Access is role-based. A newly logged-in user has **no access** and must request it; an admin approves.

| Role     | Can do                                                                                          |
|----------|-------------------------------------------------------------------------------------------------|
| `NONE`   | No access — may request `VIEWER` or `EDITOR`.                                                    |
| `VIEWER` | Read all data; may request an upgrade to `EDITOR`.                                               |
| `EDITOR` | Read + mutate data (delete rows, edit room summaries).                                           |
| `ADMIN`  | Everything an editor can, plus approve/reject access requests and manage users up to `EDITOR`.   |
| `OWNER`  | Everything an admin can, plus promote/demote admins.                                             |

- Initial owners/admins are bootstrapped from the `CONSOLE_OWNER_EMAILS` / `CONSOLE_ADMIN_EMAILS`
   env vars on first login.
- Every mutating action is recorded in the **audit log** (who, what, when), retained for **14 days**.

## Project structure

- **`backend/`** — Kotlin Spring Boot 4 application (Maven). The bot pipeline, chat ingestion, data
   console API, security and observability.
- **`frontend/`** — TypeScript Next.js app: login page + data console. All backend calls are proxied
   through Next.js API routes.
- **`chat-collector/`** — Node service that connects to the sinair.net chat, collects messages and
   events, feeds them to the backend, and delivers the bot's queued replies. See
   [chat-collector/README.md](chat-collector/README.md).
- **`chat-stubs/`** — WebSocket mock chat server for local development (fake traffic). Not deployed.
- **`google-stubs/`** — WireMock stubs for Google OAuth2 (local dev without real credentials).
- **`llm-stubs/`** — WireMock stub for the OpenAI-compatible LLM endpoint (local dev without an API key).
- **`templates/docker/`** — Docker Compose templates with Postgres and Flyway migrations.

## Running locally

The project has all external resources stubbed for comfortable local development.

### Backend

Use IntelliJ with a Run/Debug configuration — Main class `org.taonity.sinairllmbot.MainKt`, VM options
`-Dspring.profiles.active=h2,stub-google,local,stub-llm`.

From PowerShell:

```bash
mvn -pl backend spring-boot:run '-Dspring-boot.run.jvmArguments="-Dspring.profiles.active=h2,stub-google,local,stub-llm"'

```

Backend runs on port **8080**.

### Frontend

```bash
cd frontend; npm install; npm run dev

```

Frontend runs on port **3000**.

### Chat stab

```bash
cd chat-stubs; npm install; npm run dev

```

### Chat collector

```bash
cd chat-collector; npm install; npm run dev:local

```

```bash
cd chat-collector; npm install; npm run dev:prod

```

```bash
cd chat-collector; npm install; $env:LOG_LEVEL='debug'; npm run dev:prod

```

### Profiles

Each resource group has exactly one active profile; pick one per group.

| Profile       | Resource | Description                                                              |
|---------------|----------|--------------------------------------------------------------------------|
| `stub-google` | Google   | WireMock stubs for Google OAuth2 (no real credentials needed)            |
| `prod-google` | Google   | Real Google OAuth2 (requires `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`)|
| `stub-llm`    | LLM      | WireMock stub for the OpenAI-compatible LLM endpoint (no API key needed) |
| `h2`          | Database | H2 in-memory database                                                    |
| `postgres`    | Database | Postgres configuration for a local or remote instance                    |
| `local`       | General  | Common local development configuration (auto-includes `plain-log`)       |
| `plain-log`   | Logging  | Plain-text logging (included by `local`)                                 |

- **Local set:** `h2,stub-google,local,stub-llm`
- __Production set:__ `postgres,prod-google` (omit `stub-llm` and set `LLM_API_KEY` for a real provider)

## Environment variables

Configured per the profile set you run.

| Env var                                | Service  | Description                                                          |
|----------------------------------------|----------|---------------------------------------------------------------------|
| `COMPOSE_PROJECT_NAME`                 | Postgres | Name for the Docker Compose project                                 |
| `POSTGRES_USER`                        | Postgres | Used by Flyway                                                       |
| `POSTGRES_PASSWORD`                    | Postgres | Used by Flyway                                                       |
| `POSTGRES_DB`                          | Postgres | DB name                                                             |
| `POSTGRES_APP_USER`                    | Postgres | Used by the backend                                                 |
| `POSTGRES_APP_PASSWORD`                | Postgres | Used by the backend                                                 |
| `POSTGRES_PORT`                        | Postgres |                                                                     |
| `POSTGRES_ADDRESS`                     | Postgres |                                                                     |
| `GOOGLE_CLIENT_ID`                     | Backend  | From Google Cloud Console                                           |
| `GOOGLE_CLIENT_SECRET`                 | Backend  | From Google Cloud Console                                           |
| `CONSOLE_OWNER_EMAILS`                 | Backend  | Comma-separated emails bootstrapped as console owners               |
| `CONSOLE_ADMIN_EMAILS`                 | Backend  | Comma-separated emails bootstrapped as console admins (optional)    |
| `LLM_API_KEY`                          | Backend  | API key for the OpenAI-compatible LLM provider (OpenRouter)         |
| `BOT_ENABLED`                          | Backend  | Master switch for the bot pipeline                                  |
| `BOT_ROOMS`                            | Backend  | Rooms the bot participates in                                       |
| `BOT_CREATOR_USER_ID`                  | Backend  | Chat user id of the bot's creator                                   |
| `DEFAULT_SUCCESS_URL`                  | Backend  | Redirect after a successful login                                   |
| `LOGIN_URL`                            | Backend  | Redirect after a failed login                                       |
| `SERVER_SERVLET_SESSION_COOKIE_DOMAIN` | Backend  | Base domain for frontend and backend                               |
| `SERVER_SERVLET_SESSION_COOKIE_NAME`   | Backend  | Session cookie name, e.g. `JSESSIONID-STAGE`                        |
| `CSRF_COOKIE_NAME`                     | Backend  | CSRF cookie name, e.g. `XSRF-TOKEN-STAGE`                           |
| `SPRING_PROFILES_ACTIVE`               | Backend  | See the [profiles](#profiles) table                                |
| `PUBLIC_BACKEND_URL`                   | Frontend | Public backend URL for OAuth initiation                            |
| `LOCAL_BACKEND_URL`                    | Frontend | Internal backend URL for frontend server-side requests             |

### PostgreSQL database ERD diagram

<!-- mermerd-start -->
```mermaid
%%{init: {"theme":"base","look":"classic","htmlLabels":true,"fontFamily":"Verdana, sans-serif","layout":"dagre","themeVariables":{"fontSize":"12px","primaryColor":"#ffffff","primaryBorderColor":"#bcc9d8","primaryTextColor":"#20344d","lineColor":"#7196bd","edgeLabelBackground":"transparent"},"themeCSS":".schema-table{border-collapse:collapse;white-space:nowrap;font:12px/1.3 Verdana,sans-serif;color:light-dark(rgb(32 52 77),rgb(220 229 241));background-color:light-dark(rgb(255 255 255),rgb(24 32 43));border:1px solid light-dark(rgb(188 201 216),rgb(82 97 116));}.schema-table th,.schema-table td{font:12px/1.3 Verdana,sans-serif;}.schema-table th{padding:3px 6px;text-align:center;font-weight:600;background-color:light-dark(rgb(232 238 245),rgb(42 53 68));}.schema-table td{padding:2px 6px;text-align:left;border-top:1px solid light-dark(rgb(229 235 242),rgb(52 65 83));}.schema-table .typeText{background-color:light-dark(rgb(220 245 239),rgb(23 61 56));color:light-dark(rgb(15 118 110),rgb(167 223 210));}.schema-table .typeNumber{background-color:light-dark(rgb(225 237 255),rgb(31 53 87));color:light-dark(rgb(29 78 216),rgb(182 206 255));}.schema-table .typeDateTime{background-color:light-dark(rgb(238 229 255),rgb(56 42 80));color:light-dark(rgb(126 34 206),rgb(217 186 243));}.schema-table .typeBoolean{background-color:light-dark(rgb(255 240 207),rgb(68 54 30));color:light-dark(rgb(146 64 14),rgb(242 208 140));}.schema-table .typeBinary{background-color:light-dark(rgb(252 229 239),rgb(72 38 55));color:light-dark(rgb(190 24 93),rgb(240 186 208));}.schema-table .typeStructured{background-color:light-dark(rgb(232 231 255),rgb(44 43 80));color:light-dark(rgb(67 56 202),rgb(198 195 250));}.schema-table .typeOther{background-color:light-dark(rgb(224 243 252),rgb(34 60 73));color:light-dark(rgb(20 100 126),rgb(176 221 235));}.schema-table .primaryKey{background-color:light-dark(rgb(255 240 207),rgb(68 54 30));color:light-dark(rgb(146 64 14),rgb(242 208 140));}.schema-table .foreignKey{background-color:light-dark(rgb(225 237 255),rgb(31 53 87));color:light-dark(rgb(29 78 216),rgb(182 206 255));}.schema-table .uniqueKey{background-color:light-dark(rgb(238 229 255),rgb(56 42 80));color:light-dark(rgb(126 34 206),rgb(217 186 243));}.schema-edge{display:inline-block;padding:2px 4px;text-align:left;font:12px/1.3 Verdana,sans-serif;color:light-dark(rgb(32 52 77),rgb(220 229 241));background:light-dark(rgb(232 238 245),rgb(42 53 68));}","flowchart":{"padding":0.01,"wrappingWidth":100000,"rankSpacing":24,"nodeSpacing":18,"curve":"linear","subGraphTitleMargin":{"top":0,"bottom":0}}}}%%
flowchart TB
subgraph row_0[" "]
direction LR
table_0["<table class='schema-table'><thead><tr><th colspan='3'>app_user</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>google_id</td><td class='typeText'>varchar</td><td class='primaryKey'>PK</td></tr><tr><td>email</td><td class='typeText'>varchar</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>display_name</td><td class='typeText'>varchar</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>picture_url</td><td class='typeText'>varchar</td><td class='plainConstraint'>—</td></tr><tr><td>role</td><td class='typeText'>varchar(20)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>access_status</td><td class='typeText'>varchar(20)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>requested_role</td><td class='typeText'>varchar(20)</td><td class='plainConstraint'>—</td></tr></tbody></table>"]
table_1["<table class='schema-table'><thead><tr><th colspan='3'>audit_log</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>id</td><td class='typeText'>varchar(36)</td><td class='primaryKey'>PK</td></tr><tr><td>action</td><td class='typeText'>varchar(40)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>target_type</td><td class='typeText'>varchar(40)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>target_id</td><td class='typeText'>varchar(64)</td><td class='plainConstraint'>—</td></tr><tr><td>actor_google_id</td><td class='typeText'>varchar</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>actor_email</td><td class='typeText'>varchar</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>occurred_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr></tbody></table>"]
table_2["<table class='schema-table'><thead><tr><th colspan='3'>bot_config_override</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>config_key</td><td class='typeText'>varchar(200)</td><td class='primaryKey'>PK</td></tr><tr><td>value_json</td><td class='typeText'>varchar(20000)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>updated_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>updated_by</td><td class='typeText'>varchar(320)</td><td class='plainConstraint'>NOT NULL</td></tr></tbody></table>"]
table_0 ~~~ table_1
table_1 ~~~ table_2
end
style row_0 fill:transparent,stroke:transparent
subgraph row_1[" "]
direction LR
table_3["<table class='schema-table'><thead><tr><th colspan='3'>bot_config_revision</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>id</td><td class='typeText'>varchar(36)</td><td class='primaryKey'>PK</td></tr><tr><td>content_hash</td><td class='typeText'>varchar(64)</td><td class='uniqueKey'>UK · NOT NULL</td></tr><tr><td>effective_config_json</td><td class='typeText'>text</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>created_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr></tbody></table>"]
table_4["<table class='schema-table'><thead><tr><th colspan='3'>bot_config_tier</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>name</td><td class='typeText'>varchar(50)</td><td class='primaryKey'>PK</td></tr><tr><td>model</td><td class='typeText'>varchar(200)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>temperature</td><td class='typeNumber'>float8</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>max_tokens</td><td class='typeNumber'>int</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>created_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>created_by</td><td class='typeText'>varchar(320)</td><td class='plainConstraint'>NOT NULL</td></tr></tbody></table>"]
table_5["<table class='schema-table'><thead><tr><th colspan='3'>chat_event</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>id</td><td class='typeText'>varchar(36)</td><td class='primaryKey'>PK</td></tr><tr><td>dedup_key</td><td class='typeText'>varchar(255)</td><td class='uniqueKey'>UK · NOT NULL</td></tr><tr><td>room_target</td><td class='typeText'>varchar(100)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>member_id</td><td class='typeNumber'>int</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>user_id</td><td class='typeNumber'>int</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>member_name</td><td class='typeText'>varchar(100)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>member_color</td><td class='typeText'>varchar(20)</td><td class='plainConstraint'>—</td></tr><tr><td>status</td><td class='typeText'>varchar(20)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>event_data</td><td class='typeText'>text</td><td class='plainConstraint'>—</td></tr><tr><td>is_girl</td><td class='typeBoolean'>bool</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>is_moder</td><td class='typeBoolean'>bool</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>is_owner</td><td class='typeBoolean'>bool</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>event_time</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>received_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr></tbody></table>"]
table_3 ~~~ table_4
table_4 ~~~ table_5
end
style row_1 fill:transparent,stroke:transparent
row_0 ~~~ row_1
subgraph row_2[" "]
direction LR
table_6["<table class='schema-table'><thead><tr><th colspan='3'>chat_message</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>id</td><td class='typeText'>varchar(36)</td><td class='primaryKey'>PK</td></tr><tr><td>dedup_key</td><td class='typeText'>varchar(255)</td><td class='uniqueKey'>UK · NOT NULL</td></tr><tr><td>room_target</td><td class='typeText'>varchar(100)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>sender_member_id</td><td class='typeNumber'>int</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>sender_login</td><td class='typeText'>varchar(100)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>sender_color</td><td class='typeText'>varchar(20)</td><td class='plainConstraint'>—</td></tr><tr><td>message_text</td><td class='typeText'>text</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>message_style</td><td class='typeText'>varchar(20)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>recipient_member_id</td><td class='typeNumber'>int</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>sent_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>received_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>sender_user_id</td><td class='typeNumber'>int</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>source_outbound_message_id</td><td class='typeText'>varchar(36)</td><td class='plainConstraint'>—</td></tr><tr><td>source_outbound_match</td><td class='typeText'>varchar(32)</td><td class='plainConstraint'>—</td></tr><tr><td>bot_response_status</td><td class='typeText'>varchar(32)</td><td class='plainConstraint'>—</td></tr><tr><td>bot_response_reason</td><td class='typeText'>varchar(64)</td><td class='plainConstraint'>—</td></tr><tr><td>bot_response_detail</td><td class='typeText'>text</td><td class='plainConstraint'>—</td></tr><tr><td>bot_response_category</td><td class='typeText'>varchar(64)</td><td class='plainConstraint'>—</td></tr><tr><td>bot_response_deferred_at</td><td class='typeDateTime'>timestamptz</td><td class='plainConstraint'>—</td></tr><tr><td>bot_response_next_attempt_at</td><td class='typeDateTime'>timestamptz</td><td class='plainConstraint'>—</td></tr><tr><td>bot_response_updated_at</td><td class='typeDateTime'>timestamptz</td><td class='plainConstraint'>—</td></tr><tr><td>bot_response_outbound_message_id</td><td class='typeText'>varchar(36)</td><td class='plainConstraint'>—</td></tr></tbody></table>"]
table_9["<table class='schema-table'><thead><tr><th colspan='3'>pending_bot_message</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>message_id</td><td class='typeText'>varchar(36)</td><td class='primaryKey'>PK, FK</td></tr><tr><td>room_target</td><td class='typeText'>varchar(255)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>available_at</td><td class='typeDateTime'>timestamptz</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>created_at</td><td class='typeDateTime'>timestamptz</td><td class='plainConstraint'>NOT NULL</td></tr></tbody></table>"]
table_7["<table class='schema-table'><thead><tr><th colspan='3'>ignored_message</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>id</td><td class='typeText'>varchar(36)</td><td class='primaryKey'>PK</td></tr><tr><td>dedup_key</td><td class='typeText'>varchar(255)</td><td class='uniqueKey'>UK · NOT NULL</td></tr><tr><td>room_target</td><td class='typeText'>varchar(100)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>created_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr></tbody></table>"]
table_9 ~~~ table_7
end
style row_2 fill:transparent,stroke:transparent
row_1 ~~~ row_2
subgraph row_3[" "]
direction LR
table_8["<table class='schema-table'><thead><tr><th colspan='3'>outbound_message</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>id</td><td class='typeText'>varchar(36)</td><td class='primaryKey'>PK</td></tr><tr><td>room_target</td><td class='typeText'>varchar(100)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>message_text</td><td class='typeText'>text</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>reply_to_external_id</td><td class='typeText'>varchar(100)</td><td class='plainConstraint'>—</td></tr><tr><td>status</td><td class='typeText'>varchar(20)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>created_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>claimed_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>—</td></tr><tr><td>sent_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>—</td></tr><tr><td>trigger_message_id</td><td class='typeText'>varchar(36)</td><td class='plainConstraint'>—</td></tr><tr><td>pipeline_run_id</td><td class='typeText'>varchar(36)</td><td class='plainConstraint'>—</td></tr></tbody></table>"]
table_10["<table class='schema-table'><thead><tr><th colspan='3'>pipeline_run</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>id</td><td class='typeText'>varchar(36)</td><td class='primaryKey'>PK</td></tr><tr><td>pipeline_key</td><td class='typeText'>varchar(50)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>room_target</td><td class='typeText'>varchar(100)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>trigger_message_id</td><td class='typeText'>varchar(36)</td><td class='plainConstraint'>—</td></tr><tr><td>trigger_sender_login</td><td class='typeText'>varchar(150)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>trigger_text</td><td class='typeText'>text</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>outcome</td><td class='typeText'>varchar(30)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>outcome_detail</td><td class='typeText'>varchar(255)</td><td class='plainConstraint'>—</td></tr><tr><td>outbound_message_id</td><td class='typeText'>varchar(36)</td><td class='plainConstraint'>—</td></tr><tr><td>stages_json</td><td class='typeText'>text</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>created_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>total_tokens</td><td class='typeNumber'>int</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>llm_usage_json</td><td class='typeText'>text</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>json_parse_failure_count</td><td class='typeNumber'>int</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>json_parse_failures_json</td><td class='typeText'>text</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>config_revision_id</td><td class='typeText'>varchar(36)</td><td class='plainConstraint'>—</td></tr><tr><td>context_manifest_json</td><td class='typeText'>text</td><td class='plainConstraint'>NOT NULL</td></tr></tbody></table>"]
table_11["<table class='schema-table'><thead><tr><th colspan='3'>room_bot_state</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>room_target</td><td class='typeText'>varchar(100)</td><td class='primaryKey'>PK</td></tr><tr><td>muted</td><td class='typeBoolean'>bool</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>asleep</td><td class='typeBoolean'>bool</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>updated_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr></tbody></table>"]
table_8 ~~~ table_10
table_10 ~~~ table_11
end
style row_3 fill:transparent,stroke:transparent
row_2 ~~~ row_3
subgraph row_4[" "]
direction LR
table_12["<table class='schema-table'><thead><tr><th colspan='3'>room_summary</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>id</td><td class='typeText'>varchar(36)</td><td class='primaryKey'>PK</td></tr><tr><td>room_target</td><td class='typeText'>varchar(100)</td><td class='uniqueKey'>UK · NOT NULL</td></tr><tr><td>summary</td><td class='typeText'>text</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>message_count</td><td class='typeNumber'>int</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>updated_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>pipeline_run_id</td><td class='typeText'>varchar(36)</td><td class='plainConstraint'>—</td></tr><tr><td>last_message_received_at</td><td class='typeDateTime'>timestamptz</td><td class='plainConstraint'>—</td></tr><tr><td>last_message_id</td><td class='typeText'>varchar(36)</td><td class='plainConstraint'>—</td></tr></tbody></table>"]
table_13["<table class='schema-table'><thead><tr><th colspan='3'>room_summary_history</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>id</td><td class='typeText'>varchar(36)</td><td class='primaryKey'>PK</td></tr><tr><td>room_target</td><td class='typeText'>varchar(100)</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>summary</td><td class='typeText'>text</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>message_count</td><td class='typeNumber'>int</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>created_at</td><td class='typeDateTime'>timestamp</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>pipeline_run_id</td><td class='typeText'>varchar(36)</td><td class='plainConstraint'>—</td></tr></tbody></table>"]
table_12 ~~~ table_13
end
style row_4 fill:transparent,stroke:transparent
row_3 ~~~ row_4
subgraph row_5[" "]
direction LR
table_14["<table class='schema-table'><thead><tr><th colspan='3'>spring_session</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>primary_id</td><td class='typeText'>char(36)</td><td class='primaryKey'>PK</td></tr><tr><td>session_id</td><td class='typeText'>char(36)</td><td class='uniqueKey'>UK · NOT NULL</td></tr><tr><td>creation_time</td><td class='typeNumber'>bigint</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>last_access_time</td><td class='typeNumber'>bigint</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>max_inactive_interval</td><td class='typeNumber'>int</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>expiry_time</td><td class='typeNumber'>bigint</td><td class='plainConstraint'>NOT NULL</td></tr><tr><td>principal_name</td><td class='typeText'>varchar(100)</td><td class='plainConstraint'>—</td></tr></tbody></table>"]
table_15["<table class='schema-table'><thead><tr><th colspan='3'>spring_session_attributes</th></tr><tr><th>Column name</th><th>Data type</th><th>Constraints</th></tr></thead><tbody><tr><td>session_primary_id</td><td class='typeText'>char(36)</td><td class='primaryKey'>PK, FK</td></tr><tr><td>attribute_name</td><td class='typeText'>varchar(200)</td><td class='primaryKey'>PK</td></tr><tr><td>attribute_bytes</td><td class='typeBinary'>bytea</td><td class='plainConstraint'>NOT NULL</td></tr></tbody></table>"]
end
style row_5 fill:transparent,stroke:transparent
row_4 ~~~ row_5
table_15 -->|"<span class='schema-edge'>session_primary_id<br/>0..* → 1</span>"| table_14
table_9 -->|"<span class='schema-edge'>message_id<br/>0..1 → 1</span>"| table_6
classDef schemaNode fill:transparent,stroke:transparent
class table_0,table_1,table_2,table_3,table_4,table_5,table_6,table_7,table_8,table_9,table_10,table_11,table_12,table_13,table_14,table_15 schemaNode
```
<!-- mermerd-end -->

## Deployment

Docker Compose runs the backend and frontend with Postgres and Flyway migrations.

Create the shared networks required for the production environment:

```bash
docker network create prodenv-shared-internal
docker network create sinair-llm-bot-shared


```

Run with images from Docker Hub:

```bash
# Prepare the Docker Compose templates. Make sure you are on the latest released tag in git.
mvn clean -P build-automation-docker-compose-project compile -DskipTests=true
# Run the template. Make sure all required env vars are set.
docker compose -f backend/target/docker/test/docker-compose.yml up -d


```

Or build the images yourself:

```bash
# Build all modules
mvn clean -P build-docker-image,build-automation-docker-compose-project install -DskipTests=true
# Install and build the Next.js frontend image
npm install --prefix frontend/
docker build -t sinair-llm-bot-frontend frontend/
# Run the template
docker compose -f templates/docker/docker-compose.yml up -d


```

For Docker-based local development, use the override file:

```bash
cd templates/docker
docker compose -f docker-compose.yml -f docker-compose.local.yml up


```

### Production environment

The service runs on a cheap VPS. [taonity/docker-webhook](https://github.com/taonity/docker-webhook)
handles deployment into a custom production environment —
[taonity/prodenv](https://github.com/taonity/prodenv/tree/defr-prodenv).

The release workflow gates production deployments with a manual approval on the `approve-prod` job
(`environment: production`). To require approval, configure protection rules:

1. Go to **GitHub repo → Settings → Environments → "production"** (create it if it doesn't exist).
2. Enable **"Required reviewers"** and add the appropriate users or teams.

Without this, the `approve-prod` job passes automatically with no manual intervention.


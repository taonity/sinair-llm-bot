#!/usr/bin/env node
// CLI to seed / clear chat_message rows for testing.
//
// Commands:
//   node loader.js clear            Clear chat_message (and dependent pipeline_run rows)
//   node loader.js load [count]     Insert messages (default: all 500)
//   node loader.js reload [count]   Clear, then load
//   node loader.js count            Print current chat_message row count
//
// Connection (env vars, with defaults matching templates/docker/.env):
//   PGHOST=localhost PGPORT=5432 PGUSER=d PGPASSWORD=d PGDATABASE=sinair_llm_bot_db
//   or a single DATABASE_URL=postgres://d:d@localhost:5432/sinair_llm_bot_db
//
// The Postgres port is exposed on the host when the stack runs with
// docker-compose.local.yml (127.0.0.1:5432), which is the intended target.

import pg from 'pg';
import { buildMessages, TOTAL_MESSAGES } from './messages.js';

const { Client } = pg;

function makeClient() {
  if (process.env.DATABASE_URL) {
    return new Client({ connectionString: process.env.DATABASE_URL });
  }
  return new Client({
    host: process.env.PGHOST || 'localhost',
    port: Number(process.env.PGPORT || 5432),
    user: process.env.PGUSER || 'd',
    password: process.env.PGPASSWORD || 'd',
    database: process.env.PGDATABASE || 'sinair_llm_bot_db',
  });
}

async function clear(client) {
  await client.query('BEGIN');
  try {
    // pipeline_run references chat messages by id (trigger/outbound); remove it
    // first so no traces are left dangling once messages are gone.
    const traces = await client.query('DELETE FROM pipeline_run');
    const messages = await client.query('DELETE FROM chat_message');
    await client.query('COMMIT');
    console.log(`Cleared ${messages.rowCount} chat_message row(s) and ${traces.rowCount} pipeline_run row(s).`);
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  }
}

async function load(client, count) {
  const all = buildMessages();
  const limit = count == null ? all.length : Math.max(0, Math.min(count, all.length));
  const rows = all.slice(0, limit);

  const cols = [
    'id', 'dedup_key', 'room_target', 'sender_member_id', 'sender_login',
    'sender_color', 'message_text', 'message_style', 'recipient_member_id',
    'sender_user_id', 'sent_at', 'received_at',
  ];
  const perRow = cols.length;
  const batchSize = 100;
  let inserted = 0;

  await client.query('BEGIN');
  try {
    for (let start = 0; start < rows.length; start += batchSize) {
      const batch = rows.slice(start, start + batchSize);
      const values = [];
      const tuples = batch.map((r, i) => {
        const base = i * perRow;
        values.push(
          r.id, r.dedupKey, r.roomTarget, r.senderMemberId, r.senderLogin,
          r.senderColor, r.messageText, r.messageStyle, r.recipientMemberId,
          r.senderUserId, r.sentAt, r.receivedAt,
        );
        const ph = Array.from({ length: perRow }, (_, k) => `$${base + k + 1}`);
        return `(${ph.join(', ')})`;
      });
      const sql =
        `INSERT INTO chat_message (${cols.join(', ')}) VALUES ${tuples.join(', ')} ` +
        'ON CONFLICT (dedup_key) DO NOTHING';
      const res = await client.query(sql, values);
      inserted += res.rowCount;
    }
    await client.query('COMMIT');
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  }

  console.log(`Loaded ${inserted} of ${rows.length} requested message(s) (skipped duplicates: ${rows.length - inserted}).`);
}

async function count(client) {
  const res = await client.query('SELECT count(*)::int AS c FROM chat_message');
  console.log(`chat_message currently has ${res.rows[0].c} row(s).`);
}

function parseCount(arg) {
  if (arg == null) return null;
  const n = Number(arg);
  if (!Number.isInteger(n) || n < 0) {
    throw new Error(`Invalid count "${arg}" (expected a non-negative integer up to ${TOTAL_MESSAGES}).`);
  }
  return n;
}

async function main() {
  const [cmd, arg] = process.argv.slice(2);
  const client = makeClient();
  await client.connect();
  try {
    switch (cmd) {
      case 'clear':
        await clear(client);
        break;
      case 'load':
        await load(client, parseCount(arg));
        break;
      case 'reload':
        await clear(client);
        await load(client, parseCount(arg));
        break;
      case 'count':
        await count(client);
        break;
      default:
        console.log('Usage: node loader.js <clear|load [count]|reload [count]|count>');
        process.exitCode = 1;
    }
  } finally {
    await client.end();
  }
}

main().catch((err) => {
  console.error(err.message);
  process.exitCode = 1;
});

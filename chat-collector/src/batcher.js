import { config } from './config.js';
import { logger } from './logger.js';

let messageBuffer = [];
let eventBuffer = [];
let flushTimer = null;

export function resetBuffers() {
    messageBuffer = [];
    eventBuffer = [];
}

export function bufferMessage(msg) {
    messageBuffer.push(msg);
    if (messageBuffer.length >= config.batchSize) {
        flush();
    }
}

export function bufferEvent(event) {
    eventBuffer.push(event);
    logger.debug(`[batcher] Event buffered (total=${eventBuffer.length}/${config.batchSize}): status=${event.status}, member=${event.memberName}`);
    if (eventBuffer.length >= config.batchSize) {
        flush();
    }
}

export function startFlushTimer() {
    flushTimer = setInterval(() => {
        if (messageBuffer.length > 0 || eventBuffer.length > 0) {
            flush();
        }
    }, config.batchFlushInterval);
}

export function stopFlushTimer() {
    if (flushTimer) {
        clearInterval(flushTimer);
        flushTimer = null;
    }
}

async function flush() {
    const messages = messageBuffer.splice(0);
    const events = eventBuffer.splice(0);

    if (messages.length === 0 && events.length === 0) return;

    const payload = { messages, events };

    try {
        const response = await fetch(config.backendUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
        });

        if (!response.ok) {
            logger.error(`[ingest] Backend returned ${response.status}: ${await response.text()}`);
            messageBuffer.unshift(...messages);
            eventBuffer.unshift(...events);
            return;
        }

        const result = await response.json();
        logger.info(`[ingest] Stored: ${result.messagesStored} msgs, ${result.eventsStored} events | Dupes: ${result.messagesDuplicate} msgs, ${result.eventsDuplicate} events`);
    } catch (err) {
        logger.error(`[ingest] Failed to send to backend:`, err.message);
        messageBuffer.unshift(...messages);
        eventBuffer.unshift(...events);
    }
}

process.on('beforeExit', () => flush());

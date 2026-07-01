import { logger } from './logger.js';
import { startCollector, stopCollector } from './collector.js';

logger.info('[main] Chat Collector starting...');
startCollector();

// On restart/redeploy the orchestrator (e.g. Docker) sends SIGTERM. Without an explicit close
// the process exits and the OS tears the socket down without a WebSocket close frame, which the
// chat server treats as an abnormal drop and keeps our session as a lingering "orphan" — blocking
// the next start from reconnecting. Closing cleanly (code 1000) makes the server drop us at once.
let shuttingDown = false;
async function shutdown(signal) {
    if (shuttingDown) return;
    shuttingDown = true;
    logger.info(`[main] Received ${signal}, shutting down gracefully...`);
    try {
        await stopCollector();
    } catch (err) {
        logger.error('[main] Error during shutdown:', err);
    } finally {
        process.exit(0);
    }
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

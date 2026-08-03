import { logger } from './logger.js';
import { startCollector, stopCollector } from './collector.js';

logger.info('[main] Chat Collector starting...');
startCollector();

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

import { logger } from './logger.js';
import { startCollector } from './collector.js';

logger.info('[main] Chat Collector starting...');
startCollector();

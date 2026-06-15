import { config } from './config.js';

const LEVELS = { debug: 0, info: 1, warn: 2, error: 3 };

const currentLevel = LEVELS[config.logLevel] ?? LEVELS.info;

export const logger = {
    debug: (...args) => { if (currentLevel <= LEVELS.debug) console.log('[DEBUG]', ...args); },
    info:  (...args) => { if (currentLevel <= LEVELS.info)  console.log('[INFO]', ...args); },
    warn:  (...args) => { if (currentLevel <= LEVELS.warn)  console.warn('[WARN]', ...args); },
    error: (...args) => { if (currentLevel <= LEVELS.error) console.error('[ERROR]', ...args); },
};

import { describe, expect, it } from 'vitest';
import { estimateServerNowMs, isOlderThan, normalizeUnixTime } from './history.js';

describe('message history classification', () => {
    it('normalizes numeric protocol timestamps', () => {
        expect(normalizeUnixTime(1000)).toBe(1000);
        expect(normalizeUnixTime('1000')).toBe(1000);
        expect(normalizeUnixTime(undefined)).toBeNull();
        expect(normalizeUnixTime('invalid')).toBeNull();
    });

    it('classifies messages outside the live window as old', () => {
        const nowMs = 1_100_000;

        expect(isOlderThan(1000, nowMs, 60_000)).toBe(true);
        expect(isOlderThan('1050', nowMs, 60_000)).toBe(false);
        expect(isOlderThan(1040, nowMs, 60_000)).toBe(false);
    });

    it('disables age classification when the window is zero', () => {
        expect(isOlderThan(1000, 1_100_000, 0)).toBe(false);
    });

    it('estimates current server time from the join clock', () => {
        expect(estimateServerNowMs(2000, 1_000_000, 1_005_000)).toBe(2_005_000);
        expect(estimateServerNowMs(undefined, undefined, 1_005_000)).toBe(1_005_000);
    });
});
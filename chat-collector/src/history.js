export function normalizeUnixTime(value) {
    const timestamp = Number(value);
    return Number.isFinite(timestamp) ? timestamp : null;
}

export function isOlderThan(sentAt, nowMs, maxAgeMs) {
    const timestamp = normalizeUnixTime(sentAt);
    return timestamp != null && maxAgeMs > 0 && timestamp * 1000 < nowMs - maxAgeMs;
}

export function estimateServerNowMs(joinedAtSeconds, joinedLocallyAtMs, nowMs) {
    if (joinedAtSeconds == null || joinedLocallyAtMs == null) return nowMs;
    return joinedAtSeconds * 1000 + Math.max(0, nowMs - joinedLocallyAtMs);
}
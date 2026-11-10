package io.casehub.neocortex.corpus.management.impl;

public record WebCacheStatusResult(
    int cachedUrlCount,
    String oldestEntry,
    String newestEntry,
    int expiredCount
) {}

package io.casehub.neocortex.corpus.management.impl;

import java.util.List;

public record UnretrievedReport(
        List<String> neverRetrieved,
        List<String> stale,
        List<String> lowQuality,
        int neverRetrievedCount,
        int staleCount,
        int lowQualityCount) {

    public static final UnretrievedReport EMPTY =
            new UnretrievedReport(List.of(), List.of(), List.of(), 0, 0, 0);
}

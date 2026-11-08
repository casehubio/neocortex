package io.casehub.neocortex.corpus.management.impl;

import java.util.List;

public record AdaptiveResult(
        List<SearchResult> results,
        int requestedLimit,
        int availableAboveFloor,
        boolean extended,
        boolean trimmed,
        int floorFiltered,
        boolean collectionReady) {

    public AdaptiveResult(List<SearchResult> results, int requestedLimit,
                          int availableAboveFloor, boolean extended,
                          boolean trimmed, int floorFiltered) {
        this(results, requestedLimit, availableAboveFloor, extended, trimmed, floorFiltered, true);
    }

    public AdaptiveResult withResults(List<SearchResult> newResults) {
        return new AdaptiveResult(newResults, requestedLimit, availableAboveFloor,
                                  extended, trimmed, floorFiltered, collectionReady);
    }
}

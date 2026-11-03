package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;

import java.util.List;

public record HealthReport(
    List<CorpusRef> staleEntries,
    List<String> orphanedCollections,
    int totalCorpora,
    int activeCorpora,
    int archivedCorpora
) {
    public static final HealthReport HEALTHY = new HealthReport(List.of(), List.of(), 0, 0, 0);

    public boolean isHealthy() {
        return staleEntries.isEmpty() && orphanedCollections.isEmpty();
    }
}

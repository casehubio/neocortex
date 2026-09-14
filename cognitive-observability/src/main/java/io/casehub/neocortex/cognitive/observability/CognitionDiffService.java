package io.casehub.neocortex.cognitive.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class CognitionDiffService {

    private CognitionDiffService() {}

    public static GraphDiffResult diff(SnapshotStore store, String tenantId,
                                       String subgraphId, Instant from, Instant to,
                                       String source) {
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = resolveFrom(store, tenantId, from);

        List<GraphMutation> mutations = store.findMutations(tenantId, subgraphId,
            resolvedFrom, resolvedTo);

        if (source != null) {
            mutations = filterBySource(mutations, source);
        }

        var summary = computeSummary(mutations);
        return new GraphDiffResult(mutations, summary,
            new GraphDiffResult.TimeRange(resolvedFrom, resolvedTo));
    }

    private static Instant resolveFrom(SnapshotStore store, String tenantId, Instant from) {
        if (from != null) {
            return from;
        }
        return store.lastConsolidationTime(tenantId)
            .orElse(Instant.now().minus(Duration.ofHours(24)));
    }

    private static List<GraphMutation> filterBySource(List<GraphMutation> mutations, String source) {
        if (source.endsWith("*")) {
            String prefix = source.substring(0, source.length() - 1);
            return mutations.stream()
                .filter(m -> m.source().startsWith(prefix))
                .toList();
        }
        return mutations.stream()
            .filter(m -> m.source().equals(source))
            .toList();
    }

    private static GraphDiffResult.DiffSummary computeSummary(List<GraphMutation> mutations) {
        int nodesAdded = 0, nodesUpdated = 0, nodesErased = 0;
        int edgesAdded = 0, edgesRemoved = 0;
        int merges = 0, supersessions = 0, reinstated = 0;

        for (GraphMutation m : mutations) {
            switch (m) {
                case GraphMutation.NodeAdded ignored -> nodesAdded++;
                case GraphMutation.NodeUpdated ignored -> nodesUpdated++;
                case GraphMutation.NodeErased ignored -> nodesErased++;
                case GraphMutation.EdgeAdded ignored -> edgesAdded++;
                case GraphMutation.EdgeRemoved ignored -> edgesRemoved++;
                case GraphMutation.NodesMerged ignored -> merges++;
                case GraphMutation.NodeSuperseded ignored -> supersessions++;
                case GraphMutation.NodeReinstated ignored -> reinstated++;
                default -> {}
            }
        }

        return new GraphDiffResult.DiffSummary(nodesAdded, nodesUpdated, nodesErased,
            edgesAdded, edgesRemoved, merges, supersessions, reinstated);
    }
}

package io.casehub.neocortex.cognitive.observability;

import java.util.List;
import java.util.Map;

public record CognitionInspectResult(
    List<SubgraphStat> subgraphStats,
    Map<String, Integer> confidenceHistogram
) {
    public record SubgraphStat(
        String subgraphId,
        String subgraphType,
        int nodeCount,
        int edgeCount,
        double avgConfidence,
        Map<String, Long> traitDistribution
    ) {}
}

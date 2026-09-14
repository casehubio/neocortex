package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.mindmap.runtime.MindMapAnalyzer;

import java.util.List;

public record GraphHealthReport(
    List<MindMapAnalyzer.OrphanNode> orphanNodes,
    List<MindMapAnalyzer.ContradictionCluster> contradictions,
    List<MindMapAnalyzer.LowConfidenceCluster> lowConfidenceClusters,
    List<MindMapAnalyzer.UnvalidatedEdgeRatio> unvalidatedEdgeRatios,
    List<MindMapAnalyzer.StaleNode> staleNodes,
    List<MindMapAnalyzer.SparseSubgraph> densities,
    List<KCoreResult> kCores
) {
    public record KCoreResult(String subgraphId, java.util.Set<String> nodeIds, double density) {}
}

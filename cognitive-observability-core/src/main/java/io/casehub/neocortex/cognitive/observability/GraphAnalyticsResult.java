package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.mindmap.runtime.MindMapAnalyzer;

import java.util.List;

public record GraphAnalyticsResult(
        List<MindMapAnalyzer.OrphanNode> orphanNodes,
        List<MindMapAnalyzer.NodeDegree> degreeCentrality,
        MindMapAnalyzer.SparseSubgraph density,
        MindMapAnalyzer.UnvalidatedEdgeRatio unvalidatedEdgeRatio,
        List<MindMapAnalyzer.ContradictionCluster> contradictions,
        MindMapAnalyzer.LowConfidenceCluster lowConfidenceCluster,
        List<MindMapAnalyzer.BetweennessCentrality> betweennessCentrality,
        List<MindMapAnalyzer.KCore> kCores) {
}

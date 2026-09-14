package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.runtime.MindMapAnalyzer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class CognitionHealthService {

    private CognitionHealthService() {}

    public static GraphHealthReport health(MindMapStore store, String tenantId,
                                            String subgraphId, int staleThresholdDays,
                                            double lowConfidenceThreshold) {
        List<String> subgraphIds;
        if (subgraphId != null) {
            subgraphIds = List.of(subgraphId);
        } else {
            subgraphIds = store.listSubgraphs(tenantId).stream()
                .map(MindMapSubgraph::id).toList();
        }

        List<MindMapAnalyzer.OrphanNode> orphans = new ArrayList<>();
        List<MindMapAnalyzer.ContradictionCluster> contradictions = new ArrayList<>();
        List<MindMapAnalyzer.LowConfidenceCluster> lowConfClusters = new ArrayList<>();
        List<MindMapAnalyzer.UnvalidatedEdgeRatio> unvalidatedRatios = new ArrayList<>();
        List<MindMapAnalyzer.StaleNode> staleNodes = new ArrayList<>();
        List<MindMapAnalyzer.SparseSubgraph> densities = new ArrayList<>();
        List<GraphHealthReport.KCoreResult> kCores = new ArrayList<>();

        Instant now = Instant.now();
        Duration staleThreshold = Duration.ofDays(staleThresholdDays);

        for (String sgId : subgraphIds) {
            orphans.addAll(MindMapAnalyzer.orphanNodes(store, sgId, tenantId));
            contradictions.addAll(MindMapAnalyzer.contradictions(store, sgId, tenantId));
            lowConfClusters.add(MindMapAnalyzer.lowConfidenceCluster(store, sgId, tenantId, lowConfidenceThreshold));
            unvalidatedRatios.add(MindMapAnalyzer.unvalidatedEdgeRatio(store, sgId, tenantId));
            staleNodes.addAll(MindMapAnalyzer.staleNodes(store, sgId, tenantId, staleThreshold, now));
            densities.add(MindMapAnalyzer.subgraphDensity(store, sgId, tenantId));

            List<MindMapAnalyzer.KCore> rawCores = MindMapAnalyzer.kCores(store, sgId, tenantId, 2);
            for (var core : rawCores) {
                Set<String> filtered = filterSyntheticNodes(store, core.nodeIds(), tenantId);
                if (!filtered.isEmpty()) {
                    kCores.add(new GraphHealthReport.KCoreResult(sgId, filtered, core.density()));
                }
            }
        }

        return new GraphHealthReport(orphans, contradictions, lowConfClusters,
            unvalidatedRatios, staleNodes, densities, kCores);
    }

    private static Set<String> filterSyntheticNodes(MindMapStore store, Set<String> nodeIds, String tenantId) {
        return nodeIds.stream()
            .filter(id -> {
                var node = store.getNode(id, tenantId);
                return node != null && (node.traits() == null || !node.traits().contains("Summary"));
            })
            .collect(Collectors.toSet());
    }
}

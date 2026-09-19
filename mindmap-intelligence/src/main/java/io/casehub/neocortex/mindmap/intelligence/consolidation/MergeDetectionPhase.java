package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeUpdate;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
@Priority(20)
public class MergeDetectionPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(
        MergeDetectionPhase.class.getName());

    final MindMapStore store;
    final Object embeddingModel;
    final double nameThreshold;
    final double neighborThreshold;
    final double autoMergeThreshold;
    final int maxPerPass;

    @Inject
    public MergeDetectionPhase(MindMapStore store,
                                @SuppressWarnings("unused")
                                Instance<Object> embeddingModel) {
        this(store, null, 0.85, 0.3, 0.9, 10);
    }

    MergeDetectionPhase(MindMapStore store, Object embeddingModel,
                         double nameThreshold, double neighborThreshold,
                         double autoMergeThreshold, int maxPerPass) {
        this.store = store;
        this.embeddingModel = embeddingModel;
        this.nameThreshold = nameThreshold;
        this.neighborThreshold = neighborThreshold;
        this.autoMergeThreshold = autoMergeThreshold;
        this.maxPerPass = maxPerPass;
    }

    @Override
    public String name() {
        return "merge-detection";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        List<String> subgraphIds = orderedSubgraphs(tenantId, subgraphPriority);
        int mergeCount = 0;

        for (String sgId : subgraphIds) {
            if (mergeCount >= maxPerPass) break;
            List<MergeCandidate> candidates = detectCandidates(sgId, tenantId);

            for (MergeCandidate candidate : candidates) {
                if (mergeCount >= maxPerPass) break;
                if (candidate.score() >= autoMergeThreshold) {
                    try {
                        String keepId = chooseKeepNode(
                            candidate.nodeId1(), candidate.nodeId2(), tenantId);
                        String removeId = keepId.equals(candidate.nodeId1())
                            ? candidate.nodeId2() : candidate.nodeId1();
                        store.mergeNodes(keepId, removeId, tenantId);
                        mergeCount++;
                    } catch (Exception e) {
                        LOG.warning("Merge failed: " + e.getMessage());
                    }
                } else if (candidate.score() >= 0.7) {
                    flagCandidate(candidate, tenantId);
                }
            }
        }
    }

    List<MergeCandidate> detectCandidates(String subgraphId, String tenantId) {
        List<MindMapNode> nodes = store.nodesIn(subgraphId, tenantId).stream()
                                       .filter(n -> !n.traits().contains("Summary"))
                                       .toList();

        if (nodes.size() <= 1) {
            return List.of();
        }

        Map<String, Set<String>> neighborSets = bulkLoadNeighbors(nodes, tenantId);

        Map<String, List<MindMapNode>> buckets = prefixBucket(nodes);

        List<MergeCandidate> candidates = new ArrayList<>();
        for (List<MindMapNode> bucket : buckets.values()) {
            for (int i = 0; i < bucket.size(); i++) {
                for (int j = i + 1; j < bucket.size(); j++) {
                    MindMapNode a       = bucket.get(i);
                    MindMapNode b       = bucket.get(j);
                    double      nameSim = JaroWinkler.similarity(a.name(), b.name());
                    if (nameSim < nameThreshold) {continue;}

                    Set<String> neighborsA      = neighborSets.getOrDefault(a.id(), Set.of());
                    Set<String> neighborsB      = neighborSets.getOrDefault(b.id(), Set.of());
                    double      neighborOverlap = jaccard(neighborsA, neighborsB);

                    double combined = 0.6 * nameSim + 0.4 * neighborOverlap;
                    if (combined >= 0.6) {
                        String reason = neighborOverlap > 0
                                        ? "name+neighbors" : "name-similarity";
                        candidates.add(new MergeCandidate(
                                a.id(), b.id(), combined, reason, Instant.now()));
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(MergeCandidate::score).reversed());
        return candidates;
    }

    private Map<String, Set<String>> bulkLoadNeighbors(List<MindMapNode> nodes,
                                                         String tenantId) {
        Map<String, Set<String>> result = new HashMap<>();
        for (MindMapNode node : nodes) {
            Set<String> ids = store.neighbors(node.id(), tenantId).stream()
                .map(edge -> edge.sourceNodeId().equals(node.id())
                    ? edge.targetNodeId() : edge.sourceNodeId())
                .collect(Collectors.toSet());
            result.put(node.id(), ids);
        }
        return result;
    }

    private static Map<String, List<MindMapNode>> prefixBucket(List<MindMapNode> nodes) {
        Map<String, List<MindMapNode>> buckets = new HashMap<>();
        for (MindMapNode node : nodes) {
            String name = node.name().trim().toLowerCase();
            String key = name.length() >= 3 ? name.substring(0, 3) : name;
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
        }
        return buckets;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private String chooseKeepNode(String id1, String id2, String tenantId) {
        MindMapNode n1 = store.getNode(id1, tenantId);
        MindMapNode n2 = store.getNode(id2, tenantId);
        int s1 = n1.property("storageStrength").map(Integer::parseInt).orElse(0);
        int s2 = n2.property("storageStrength").map(Integer::parseInt).orElse(0);
        return s1 >= s2 ? id1 : id2;
    }

    private void flagCandidate(MergeCandidate candidate, String tenantId) {
        try {
            store.updateNode(candidate.nodeId1(),
                NodeUpdate.empty().withPropertiesToSet(Map.of(
                    "mergeCandidate", candidate.nodeId2())),
                tenantId);
            store.updateNode(candidate.nodeId2(),
                NodeUpdate.empty().withPropertiesToSet(Map.of(
                    "mergeCandidate", candidate.nodeId1())),
                tenantId);
        } catch (Exception e) {
            LOG.fine("Could not flag merge candidate: " + e.getMessage());
        }
    }

    private List<String> orderedSubgraphs(String tenantId, List<String> priority) {
        List<String> all = store.listSubgraphs(tenantId).stream()
            .map(MindMapSubgraph::id).collect(Collectors.toCollection(ArrayList::new));
        List<String> ordered = new ArrayList<>();
        for (String sgId : priority) {
            if (all.remove(sgId)) ordered.add(sgId);
        }
        ordered.addAll(all);
        return ordered;
    }
}

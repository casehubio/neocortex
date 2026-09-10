package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.*;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.*;
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

        List<MergeCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                MindMapNode a = nodes.get(i);
                MindMapNode b = nodes.get(j);
                double nameSim = JaroWinkler.similarity(a.name(), b.name());
                if (nameSim < nameThreshold) continue;

                Set<String> neighborsA = neighborIds(a.id(), tenantId);
                Set<String> neighborsB = neighborIds(b.id(), tenantId);
                double neighborOverlap = jaccard(neighborsA, neighborsB);

                double combined = 0.6 * nameSim + 0.4 * neighborOverlap;
                if (combined >= 0.6) {
                    String reason = neighborOverlap > 0 ? "name+neighbors" : "name-similarity";
                    candidates.add(new MergeCandidate(
                        a.id(), b.id(), combined, reason, Instant.now()));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(MergeCandidate::score).reversed());
        return candidates;
    }

    private Set<String> neighborIds(String nodeId, String tenantId) {
        return store.neighbors(nodeId, tenantId).stream()
            .map(edge -> edge.sourceNodeId().equals(nodeId) ? edge.targetNodeId() : edge.sourceNodeId())
            .collect(Collectors.toSet());
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

package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.MindMapCapability;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.neocortex.mindmap.ValidationTier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class MindMapAnalyzer {

    private MindMapAnalyzer() {}

    private static void requireAnalysis(MindMapStore store) {
        store.requireCapability(MindMapCapability.GRAPH_ANALYSIS);
    }


    // --- Signal records ---

    public record OrphanNode(String nodeId, String name, String subgraphId) {}

    public record NodeDegree(String nodeId, String name, int degree) {}

    public record SparseSubgraph(String subgraphId, String name, int nodeCount, int edgeCount, double density) {}

    public record UnvalidatedEdgeRatio(String subgraphId, String name, int total, int unvalidated, double ratio) {}

    public record StaleNode(String nodeId, String name, Instant lastUpdated, Duration age) {}

    public record ContradictionCluster(String nodeId, String name, String edgeType, List<String> conflictingTargets) {}

    public record LowConfidenceCluster(String subgraphId, String name, int total, int lowConfidence, double ratio) {}

    public record DanglingNodeRef(String nodeId, String name, NodeRef ref) {}

    public record BetweennessCentrality(String nodeId, String name, double score) {}

    // --- Structural signals ---

    public static List<OrphanNode> orphanNodes(MindMapStore store, String subgraphId, String tenantId) {
        requireAnalysis(store);
        List<OrphanNode> orphans = new ArrayList<>();
        for (MindMapNode node : store.nodesIn(subgraphId, tenantId)) {
            if (store.neighbors(node.id(), tenantId).isEmpty()) {
                orphans.add(new OrphanNode(node.id(), node.name(), subgraphId));
            }
        }
        return orphans;
    }

    public static List<NodeDegree> degreeCentrality(MindMapStore store, String subgraphId, String tenantId) {
        requireAnalysis(store);
        List<NodeDegree> degrees = new ArrayList<>();
        for (MindMapNode node : store.nodesIn(subgraphId, tenantId)) {
            int degree = store.neighbors(node.id(), tenantId).size();
            degrees.add(new NodeDegree(node.id(), node.name(), degree));
        }
        degrees.sort(Comparator.comparingInt(NodeDegree::degree).reversed());
        return degrees;
    }

    public static SparseSubgraph subgraphDensity(MindMapStore store, String subgraphId, String tenantId) {
        requireAnalysis(store);
        MindMapSubgraph sg = store.getSubgraph(subgraphId, tenantId);
        List<MindMapNode> nodes = store.nodesIn(subgraphId, tenantId);
        int nodeCount = nodes.size();
        if (nodeCount <= 1) {
            return new SparseSubgraph(subgraphId, sg.name(), nodeCount, 0, 0.0);
        }
        Set<String> edgeIds = new HashSet<>();
        for (MindMapNode node : nodes) {
            for (MindMapEdge edge : store.neighbors(node.id(), tenantId)) {
                edgeIds.add(edge.id());
            }
        }
        int edgeCount = edgeIds.size();
        double maxEdges = (double) nodeCount * (nodeCount - 1);
        double density = maxEdges > 0 ? edgeCount / maxEdges : 0.0;
        return new SparseSubgraph(subgraphId, sg.name(), nodeCount, edgeCount, density);
    }

    // --- Quality signals ---

    public static UnvalidatedEdgeRatio unvalidatedEdgeRatio(MindMapStore store, String subgraphId, String tenantId) {
        requireAnalysis(store);
        MindMapSubgraph sg = store.getSubgraph(subgraphId, tenantId);
        Set<String> seen = new HashSet<>();
        int total = 0;
        int unvalidated = 0;
        for (MindMapNode node : store.nodesIn(subgraphId, tenantId)) {
            for (MindMapEdge edge : store.neighbors(node.id(), tenantId)) {
                if (seen.add(edge.id())) {
                    total++;
                    if (edge.tier() == ValidationTier.UNVALIDATED) {
                        unvalidated++;
                    }
                }
            }
        }
        double ratio = total > 0 ? (double) unvalidated / total : 0.0;
        return new UnvalidatedEdgeRatio(subgraphId, sg.name(), total, unvalidated, ratio);
    }

    public static List<ContradictionCluster> contradictions(MindMapStore store, String subgraphId, String tenantId) {
        requireAnalysis(store);
        List<ContradictionCluster> results = new ArrayList<>();
        for (MindMapNode node : store.nodesIn(subgraphId, tenantId)) {
            Map<String, List<String>> targetsByType = new HashMap<>();
            for (MindMapEdge edge : store.neighbors(node.id(), tenantId)) {
                if (edge.sourceNodeId().equals(node.id())) {
                    targetsByType.computeIfAbsent(edge.edgeType(), k -> new ArrayList<>())
                        .add(edge.targetNodeId());
                }
            }
            for (var entry : targetsByType.entrySet()) {
                if (entry.getValue().size() > 1) {
                    results.add(new ContradictionCluster(
                        node.id(), node.name(), entry.getKey(), List.copyOf(entry.getValue())));
                }
            }
        }
        return results;
    }

    public static LowConfidenceCluster lowConfidenceCluster(MindMapStore store, String subgraphId,
                                                             String tenantId, double threshold) {
        requireAnalysis(store);
        MindMapSubgraph sg = store.getSubgraph(subgraphId, tenantId);
        List<MindMapNode> nodes = store.nodesIn(subgraphId, tenantId);
        int total = nodes.size();
        int low = 0;
        for (MindMapNode node : nodes) {
            if (node.confidence().value() < threshold) {
                low++;
            }
        }
        double ratio = total > 0 ? (double) low / total : 0.0;
        return new LowConfidenceCluster(subgraphId, sg.name(), total, low, ratio);
    }

    // --- Temporal signals ---

    public static List<StaleNode> staleNodes(MindMapStore store, String subgraphId,
                                              String tenantId, Duration staleThreshold, Instant now) {
        requireAnalysis(store);
        List<StaleNode> stale = new ArrayList<>();
        for (MindMapNode node : store.nodesIn(subgraphId, tenantId)) {
            Instant lastUpdated = node.confidence().decayReference() != null ? node.confidence().decayReference() : node.updatedAt();
            Duration age = Duration.between(lastUpdated, now);
            if (age.compareTo(staleThreshold) > 0) {
                stale.add(new StaleNode(node.id(), node.name(), lastUpdated, age));
            }
        }
        stale.sort(Comparator.comparing(StaleNode::age).reversed());
        return stale;
    }

    // --- Centrality signals ---

    public static List<BetweennessCentrality> betweennessCentrality(MindMapStore store,
                                                                     String subgraphId, String tenantId) {
        requireAnalysis(store);
        List<MindMapNode> nodes = store.nodesIn(subgraphId, tenantId);
        if (nodes.size() <= 2) {
            return nodes.stream()
                .map(n -> new BetweennessCentrality(n.id(), n.name(), 0.0))
                .toList();
        }

        Map<String, Set<String>> adjacency = new HashMap<>();
        for (MindMapNode node : nodes) {
            Set<String> neighbors = new HashSet<>();
            for (MindMapEdge edge : store.neighbors(node.id(), tenantId)) {
                String other = edge.sourceNodeId().equals(node.id())
                    ? edge.targetNodeId() : edge.sourceNodeId();
                neighbors.add(other);
            }
            adjacency.put(node.id(), neighbors);
        }

        Map<String, Double> centrality = new HashMap<>();
        for (MindMapNode node : nodes) {
            centrality.put(node.id(), 0.0);
        }

        List<String> nodeIds = nodes.stream().map(MindMapNode::id).toList();

        for (String source : nodeIds) {
            Map<String, Integer> dist = new HashMap<>();
            Map<String, List<String>> pred = new HashMap<>();
            Map<String, Double> sigma = new HashMap<>();
            for (String n : nodeIds) {
                dist.put(n, -1);
                pred.put(n, new ArrayList<>());
                sigma.put(n, 0.0);
            }
            dist.put(source, 0);
            sigma.put(source, 1.0);

            Queue<String> queue = new ArrayDeque<>();
            Deque<String> stack = new ArrayDeque<>();
            queue.add(source);

            while (!queue.isEmpty()) {
                String v = queue.poll();
                stack.push(v);
                for (String w : adjacency.getOrDefault(v, Set.of())) {
                    if (dist.get(w) < 0) {
                        dist.put(w, dist.get(v) + 1);
                        queue.add(w);
                    }
                    if (dist.get(w) == dist.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        pred.get(w).add(v);
                    }
                }
            }

            Map<String, Double> delta = new HashMap<>();
            for (String n : nodeIds) {
                delta.put(n, 0.0);
            }
            while (!stack.isEmpty()) {
                String w = stack.pop();
                for (String v : pred.get(w)) {
                    delta.put(v, delta.get(v) + (sigma.get(v) / sigma.get(w)) * (1 + delta.get(w)));
                }
                if (!w.equals(source)) {
                    centrality.put(w, centrality.get(w) + delta.get(w));
                }
            }
        }

        double n = nodeIds.size();
        double norm = (n - 1) * (n - 2);

        return nodes.stream()
            .map(node -> new BetweennessCentrality(
                node.id(), node.name(),
                norm > 0 ? centrality.get(node.id()) / norm : 0.0))
            .sorted(Comparator.comparingDouble(BetweennessCentrality::score).reversed())
            .toList();
    }
}

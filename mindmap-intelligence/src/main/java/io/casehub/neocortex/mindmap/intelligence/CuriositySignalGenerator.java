package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.runtime.MindMapAnalyzer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@ApplicationScoped
public class CuriositySignalGenerator implements CuriositySignalProvider {

    private static final double PROXIMITY_SCALE = 7.0;
    private static final Duration STALE_THRESHOLD = Duration.ofDays(90);
    private static final int MAX_BFS_DEPTH = 4;
    private static final int TOP_CENTRALITY = 3;

    private final MindMapStore store;

    @Inject
    public CuriositySignalGenerator(MindMapStore store) {
        this.store = store;
    }

    @Override
    public List<CuriositySignal> computeSignals(String tenantId, Set<String> recentEntityIds) {
        List<MindMapSubgraph> subgraphs = store.listSubgraphs(tenantId);
        if (subgraphs.isEmpty()) return List.of();

        List<CuriositySignal> signals = new ArrayList<>();

        for (MindMapSubgraph sg : subgraphs) {
            collectStructuralSignals(sg, tenantId, signals);
            collectQualitySignals(sg, tenantId, signals);
            collectTemporalSignals(sg, tenantId, signals);
            collectCentralitySignals(sg, tenantId, signals);
            collectProximitySignals(sg, tenantId, signals);
        }

        applyAffectDampening(signals, tenantId);
        applyTopicalDistanceDampening(signals, recentEntityIds, tenantId);

        signals.sort(Comparator.comparingDouble(CuriositySignal::score).reversed());
        return signals;
    }

    private void collectStructuralSignals(MindMapSubgraph sg, String tenantId,
                                           List<CuriositySignal> signals) {
        for (var orphan : MindMapAnalyzer.orphanNodes(store, sg.id(), tenantId)) {
            signals.add(new CuriositySignal(
                SignalCategory.STRUCTURAL, 1.0,
                orphan.nodeId(), sg.id(),
                "What is " + orphan.name() + "'s connection to other entities?",
                "Orphan node: " + orphan.name()));
        }

        var density = MindMapAnalyzer.subgraphDensity(store, sg.id(), tenantId);
        if (density != null && density.density() < 0.1 && density.nodeCount() > 1) {
            signals.add(new CuriositySignal(
                SignalCategory.STRUCTURAL, 1.0 - density.density(),
                null, sg.id(),
                "What else is part of " + sg.name() + "?",
                "Sparse subgraph: " + sg.name() + " (density " +
                    String.format("%.2f", density.density()) + ")"));
        }
    }

    private void collectQualitySignals(MindMapSubgraph sg, String tenantId,
                                        List<CuriositySignal> signals) {
        for (var contradiction : MindMapAnalyzer.contradictions(store, sg.id(), tenantId)) {
            String targets = String.join(" vs ", contradiction.conflictingTargets());
            signals.add(new CuriositySignal(
                SignalCategory.QUALITY, 0.9,
                contradiction.nodeId(), sg.id(),
                "Is the information about " + contradiction.name() + " still accurate? "
                    + "Multiple " + contradiction.edgeType() + " targets: " + targets,
                "Contradiction: " + contradiction.name() + " has " +
                    contradiction.conflictingTargets().size() + " " +
                    contradiction.edgeType() + " targets"));
        }

        var lowConf = MindMapAnalyzer.lowConfidenceCluster(store, sg.id(), tenantId, 0.5);
        if (lowConf != null && lowConf.ratio() > 0.5) {
            signals.add(new CuriositySignal(
                SignalCategory.QUALITY, lowConf.ratio(),
                null, sg.id(),
                "Much of the knowledge in " + sg.name() + " is uncertain. Can you confirm?",
                "Low confidence cluster: " + lowConf.lowConfidence() +
                    "/" + lowConf.total() + " nodes below threshold"));
        }

        var unvalidated = MindMapAnalyzer.unvalidatedEdgeRatio(store, sg.id(), tenantId);
        if (unvalidated != null && unvalidated.ratio() > 0.3) {
            signals.add(new CuriositySignal(
                SignalCategory.QUALITY, unvalidated.ratio(),
                null, sg.id(),
                "Is the information about " + sg.name() + " still accurate? "
                    + unvalidated.unvalidated() + " unvalidated relationships",
                "Unvalidated edge ratio: " + String.format("%.0f%%", unvalidated.ratio() * 100)));
        }
    }

    private void collectTemporalSignals(MindMapSubgraph sg, String tenantId,
                                         List<CuriositySignal> signals) {
        Instant now = Instant.now();
        for (var stale : MindMapAnalyzer.staleNodes(store, sg.id(), tenantId, STALE_THRESHOLD, now)) {
            double staleDays = stale.age().toDays();
            double score = Math.min(1.0, staleDays / 180.0);
            signals.add(new CuriositySignal(
                SignalCategory.TEMPORAL, score,
                stale.nodeId(), sg.id(),
                "Is " + stale.name() + " still relevant? Last updated " +
                    stale.age().toDays() + " days ago.",
                "Stale node: " + stale.name()));
        }
    }

    private void collectCentralitySignals(MindMapSubgraph sg, String tenantId,
                                           List<CuriositySignal> signals) {
        var betweenness = MindMapAnalyzer.betweennessCentrality(store, sg.id(), tenantId);
        int count = 0;
        for (var bc : betweenness) {
            if (count >= TOP_CENTRALITY || bc.score() <= 0) break;
            signals.add(new CuriositySignal(
                SignalCategory.CENTRALITY, Math.min(1.0, bc.score()),
                bc.nodeId(), sg.id(),
                "Tell me more about " + bc.name() + " — it connects many areas of knowledge.",
                "High betweenness centrality: " + bc.name()));
            count++;
        }

        var degrees = MindMapAnalyzer.degreeCentrality(store, sg.id(), tenantId);
        count = 0;
        for (var deg : degrees) {
            if (count >= TOP_CENTRALITY || deg.degree() <= 1) break;
            signals.add(new CuriositySignal(
                SignalCategory.CENTRALITY, Math.min(1.0, deg.degree() / 10.0),
                deg.nodeId(), sg.id(),
                "Tell me more about " + deg.name() + " — it connects many areas of knowledge.",
                "High degree centrality: " + deg.name() + " (" + deg.degree() + " edges)"));
            count++;
        }
    }

    private void collectProximitySignals(MindMapSubgraph sg, String tenantId,
                                          List<CuriositySignal> signals) {
        Instant now = Instant.now();
        for (MindMapNode node : store.nodesIn(sg.id(), tenantId)) {
            if (node.validFrom() != null && node.validFrom().isAfter(now)) {
                double daysUntil = Duration.between(now, node.validFrom()).toHours() / 24.0;
                double score = 1.0 / (1.0 + daysUntil / PROXIMITY_SCALE);
                signals.add(new CuriositySignal(
                    SignalCategory.PROXIMITY, score,
                    node.id(), sg.id(),
                    "What should I know about " + node.name() + " before it happens?",
                    "Approaching event: " + node.name()));
            }

            if (node.validUntil() != null && node.validUntil().isBefore(now)) {
                signals.add(new CuriositySignal(
                    SignalCategory.TEMPORAL, 0.8,
                    node.id(), sg.id(),
                    "Did " + node.name() + " happen? What was the outcome?",
                    "Past event: " + node.name()));
            }
        }
    }

    private void applyAffectDampening(List<CuriositySignal> signals, String tenantId) {
        for (int i = 0; i < signals.size(); i++) {
            CuriositySignal signal = signals.get(i);
            if (signal.category() == SignalCategory.PROXIMITY) continue;
            if (signal.targetNodeId() == null) continue;

            MindMapNode node = store.getNode(signal.targetNodeId(), tenantId);
            if (node != null && node.pleasure() != null && node.pleasure() < 0) {
                double factor = Math.max(0.1, 1.0 + node.pleasure());
                signals.set(i, new CuriositySignal(
                    signal.category(), signal.score() * factor,
                    signal.targetNodeId(), signal.targetSubgraphId(),
                    signal.question(), signal.description()));
            }
        }
    }

    private void applyTopicalDistanceDampening(List<CuriositySignal> signals,
                                                Set<String> recentEntityIds,
                                                String tenantId) {
        if (recentEntityIds == null || recentEntityIds.isEmpty()) return;

        Map<String, Integer> distanceCache = new HashMap<>();
        for (int i = 0; i < signals.size(); i++) {
            CuriositySignal signal = signals.get(i);
            if (signal.targetNodeId() == null) continue;
            if (recentEntityIds.contains(signal.targetNodeId())) continue;

            int distance = distanceCache.computeIfAbsent(signal.targetNodeId(),
                nodeId -> bfsDistance(nodeId, recentEntityIds, tenantId));
            if (distance > 0 && distance <= MAX_BFS_DEPTH) {
                double factor = 1.0 / (1.0 + distance);
                signals.set(i, new CuriositySignal(
                    signal.category(), signal.score() * factor,
                    signal.targetNodeId(), signal.targetSubgraphId(),
                    signal.question(), signal.description()));
            }
        }
    }

    private int bfsDistance(String fromNodeId, Set<String> targetIds, String tenantId) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        Queue<Integer> depths = new LinkedList<>();
        queue.add(fromNodeId);
        depths.add(0);
        visited.add(fromNodeId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int depth = depths.poll();

            if (targetIds.contains(current)) return depth;
            if (depth >= MAX_BFS_DEPTH) continue;

            for (MindMapEdge edge : store.neighbors(current, tenantId)) {
                String neighbor = edge.sourceNodeId().equals(current)
                    ? edge.targetNodeId() : edge.sourceNodeId();
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                    depths.add(depth + 1);
                }
            }
        }
        return MAX_BFS_DEPTH + 1;
    }
}

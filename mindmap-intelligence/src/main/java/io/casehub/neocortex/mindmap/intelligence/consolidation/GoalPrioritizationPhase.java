package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SignalCategory;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(38)
public class GoalPrioritizationPhase implements ConsolidationPhase {

    private static final Logger      LOG                          = Logger.getLogger(GoalPrioritizationPhase.class.getName());
    private static final Set<String> IMPORTANCE_EDGE_TYPES        = Set.of("contributes-to", "enables");
    private static final double      W_URGENCY                    = 0.3;
    private static final double      W_FEASIBILITY                = 0.2;
    private static final double      W_AFFECTIVE                  = 0.2;
    private static final double      W_IMPORTANCE                 = 0.3;
    static final         double      DORMANT_CONFIDENCE_THRESHOLD = 0.4;
    static final         double      ABANDON_CONFIDENCE_THRESHOLD = 0.2;

    private final MindMapStore store;
    private final Clock        clock;
    private final List<AttentionSignal> pendingSignals = new ArrayList<>();

    @Inject
    public GoalPrioritizationPhase(Instance<MindMapStore> store) {
        this.store = store.isResolvable() ? store.get() : null;
        this.clock = Clock.systemUTC();
    }

    public GoalPrioritizationPhase(MindMapStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public GoalPrioritizationPhase(MindMapStore store) {
        this(store, Clock.systemUTC());
    }

    @Override
    public String name() {
        return "goal-prioritization";
    }

    @Override
    public void beginTick() {
        pendingSignals.clear();
    }

    @Override
    public List<AttentionSignal> signals() {
        var result = List.copyOf(pendingSignals);
        pendingSignals.clear();
        return result;
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (store == null) {return;}

        String goalSgId = findGoalSubgraph(tenantId);
        if (goalSgId == null) {return;}

        List<MindMapNode> goalNodes = store.nodesIn(goalSgId, tenantId);
        List<MindMapNode> activeGoals = goalNodes.stream()
                                                 .filter(n -> "active".equals(n.property("status").orElse(null)))
                                                 .toList();

        if (!activeGoals.isEmpty()) {
            prioritize(activeGoals, tenantId);
        }

        decay(goalNodes, tenantId);
    }

    private void prioritize(List<MindMapNode> activeGoals, String tenantId) {
        Instant now        = clock.instant();
        int     maxInbound = 1;
        for (MindMapNode node : activeGoals) {
            int inbound = countImportanceEdges(node, tenantId);
            if (inbound > maxInbound) {maxInbound = inbound;}
        }

        for (MindMapNode node : activeGoals) {
            double urgency     = GoalUrgency.computeUrgency(node, now);
            double feasibility = node.property("feasibility").map(Double::parseDouble).orElse(0.0);

            double pleasure         = node.pleasure() != null ? node.pleasure() : 0.0;
            double dominance        = node.dominance() != null ? node.dominance() : 0.0;
            double affectiveValence = (pleasure + dominance + 2.0) / 4.0;

            double importance = (double) countImportanceEdges(node, tenantId) / maxInbound;

            double priority = W_URGENCY * urgency
                              + W_FEASIBILITY * feasibility
                              + W_AFFECTIVE * affectiveValence
                              + W_IMPORTANCE * importance;

            priority = Math.max(0.0, Math.min(1.0, priority));

            String principalId = node.property("agent-id").orElse(null);

            if (urgency > 0.7) {
                pendingSignals.add(new AttentionSignal(
                        principalId, tenantId, SignalCategory.URGENCY_SPIKE,
                        node.id(), node.name(), urgency,
                        "urgency rose to " + String.format("%.2f", urgency)));
            }

            double previousPriority = node.property("priority").map(Double::parseDouble).orElse(0.0);
            if (Math.abs(priority - previousPriority) > 0.2) {
                pendingSignals.add(new AttentionSignal(
                        principalId, tenantId, SignalCategory.PRIORITY_SHIFT,
                        node.id(), node.name(), Math.abs(priority - previousPriority),
                        "priority shifted from " + String.format("%.2f", previousPriority) + " to " + String.format("%.2f", priority)));
            }

            store.updateNode(node.id(),
                             NodeUpdate.empty().withPropertiesToSet(
                                     Map.of("priority", Double.toString(priority))),
                             tenantId);
        }
    }

    private void decay(List<MindMapNode> allGoalNodes, String tenantId) {
        for (MindMapNode node : allGoalNodes) {
            if (!"active".equals(node.property("status").orElse(null))) {continue;}

            Confidence conf = node.confidence();
            if (conf == null || conf.value() >= DORMANT_CONFIDENCE_THRESHOLD) {continue;}

            double pleasure = node.pleasure() != null ? node.pleasure() : 0.0;
            if (pleasure > 0) {continue;}

            boolean isLinked    = node.property("eidos-goal-name").isPresent();
            String  principalId = node.property("agent-id").orElse(null);

            if (conf.value() < ABANDON_CONFIDENCE_THRESHOLD && pleasure < 0) {
                if (isLinked) {
                    store.updateNode(node.id(),
                                     NodeUpdate.empty().withPropertiesToSet(
                                             Map.of("decay-signal", "abandon")),
                                     tenantId);
                } else {
                    store.updateNode(node.id(),
                                     NodeUpdate.empty().withPropertiesToSet(
                                             Map.of("status", "abandoned",
                                                    "abandonment-reason", "Extended inactivity with declining affect")),
                                     tenantId);
                }
                pendingSignals.add(new AttentionSignal(
                        principalId, tenantId, SignalCategory.DECAY_DETECTED,
                        node.id(), node.name(), 1.0 - conf.value(),
                        "goal abandoned — confidence " + String.format("%.2f", conf.value())));
                LOG.fine("Decay: abandoned goal '" + node.name() + "'");
            } else {
                if (isLinked) {
                    store.updateNode(node.id(),
                                     NodeUpdate.empty().withPropertiesToSet(
                                             Map.of("decay-signal", "dormant")),
                                     tenantId);
                } else {
                    store.updateNode(node.id(),
                                     NodeUpdate.empty().withPropertiesToSet(
                                             Map.of("status", "dormant")),
                                     tenantId);
                }
                pendingSignals.add(new AttentionSignal(
                        principalId, tenantId, SignalCategory.DECAY_DETECTED,
                        node.id(), node.name(), 1.0 - conf.value(),
                        "goal dormant — confidence " + String.format("%.2f", conf.value())));
                LOG.fine("Decay: dormant goal '" + node.name() + "'");
            }
        }
    }

    private int countImportanceEdges(MindMapNode node, String tenantId) {
        int count = 0;
        for (String edgeType : IMPORTANCE_EDGE_TYPES) {
            List<MindMapEdge> edges = store.neighbors(node.id(), edgeType, tenantId);
            count += edges.stream()
                          .filter(e -> e.targetNodeId().equals(node.id()))
                          .count();
        }
        return count;
    }

    private String findGoalSubgraph(String tenantId) {
        for (MindMapSubgraph sg : store.listSubgraphs(tenantId)) {
            if (SubgraphTypes.GOAL.equals(sg.type())) {
                return sg.id();
            }
        }
        return null;
    }
}

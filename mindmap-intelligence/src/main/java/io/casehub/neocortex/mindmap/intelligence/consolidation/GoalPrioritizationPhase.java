package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(38)
public class GoalPrioritizationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(GoalPrioritizationPhase.class.getName());
    private static final Set<String> IMPORTANCE_EDGE_TYPES = Set.of("contributes-to", "enables");
    private static final double W_URGENCY = 0.3;
    private static final double W_FEASIBILITY = 0.2;
    private static final double W_AFFECTIVE = 0.2;
    private static final double W_IMPORTANCE = 0.3;

    private final MindMapStore store;

    @Inject
    public GoalPrioritizationPhase(Instance<MindMapStore> store) {
        this.store = store.isResolvable() ? store.get() : null;
    }

    public GoalPrioritizationPhase(MindMapStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "goal-prioritization";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (store == null) return;

        String goalSgId = findGoalSubgraph(tenantId);
        if (goalSgId == null) return;

        List<MindMapNode> goalNodes = store.nodesIn(goalSgId, tenantId);
        List<MindMapNode> activeGoals = goalNodes.stream()
                .filter(n -> "active".equals(n.property("status").orElse(null)))
                .toList();

        if (activeGoals.isEmpty()) return;

        int maxInbound = 1;
        for (MindMapNode node : activeGoals) {
            int inbound = countImportanceEdges(node, tenantId);
            if (inbound > maxInbound) maxInbound = inbound;
        }

        for (MindMapNode node : activeGoals) {
            double urgency = node.property("urgency").map(Double::parseDouble).orElse(0.0);
            double feasibility = node.property("feasibility").map(Double::parseDouble).orElse(0.0);

            double pleasure = node.pleasure() != null ? node.pleasure() : 0.0;
            double dominance = node.dominance() != null ? node.dominance() : 0.0;
            double affectiveValence = (pleasure + dominance + 2.0) / 4.0;

            double importance = (double) countImportanceEdges(node, tenantId) / maxInbound;

            double priority = W_URGENCY * urgency
                    + W_FEASIBILITY * feasibility
                    + W_AFFECTIVE * affectiveValence
                    + W_IMPORTANCE * importance;

            priority = Math.max(0.0, Math.min(1.0, priority));

            store.updateNode(node.id(),
                    NodeUpdate.empty().withPropertiesToSet(
                            Map.of("priority", Double.toString(priority))),
                    tenantId);
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

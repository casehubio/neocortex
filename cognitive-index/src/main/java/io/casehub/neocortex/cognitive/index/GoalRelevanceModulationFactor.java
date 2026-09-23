package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.ModulationFactor;
import io.casehub.neocortex.cognitive.ModulationProfile;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.SubgraphTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GoalRelevanceModulationFactor implements ModulationFactor<Memory> {

    private static final int MAX_DEPTH = 4;

    private final MindMapStore store;
    private final String tenantId;
    private volatile List<MindMapNode> activeGoals;

    public GoalRelevanceModulationFactor(MindMapStore store, String tenantId) {
        this.store = store;
        this.tenantId = tenantId;
    }

    @Override
    public double apply(Memory item, ModulationProfile<Memory> profile) {
        List<MindMapNode> goals = getActiveGoals();
        if (goals.isEmpty()) {return 1.0;}

        String      entityName = item.subject().id();
        MindMapNode entityNode = findEntityNode(entityName);
        if (entityNode == null) {return 1.0;}

        int minDistance = shortestPathToGoal(entityNode, goals);
        if (minDistance <= 0) {return 1.0;}
        if (minDistance == 1) {return 1.0;}
        if (minDistance == 2) {return 0.7;}
        if (minDistance == 3) {return 0.4;}
        return 0.0;
    }

    public void refreshGoals() {
        this.activeGoals = null;
    }

    private List<MindMapNode> getActiveGoals() {
        List<MindMapNode> cached = this.activeGoals;
        if (cached != null) return cached;

        String goalSgId = findGoalSubgraph();
        if (goalSgId == null) {
            this.activeGoals = List.of();
            return List.of();
        }

        List<MindMapNode> goals = store.nodesIn(goalSgId, tenantId).stream()
                .filter(n -> "active".equals(n.property("status").orElse(null)))
                .toList();
        this.activeGoals = goals;
        return goals;
    }

    private MindMapNode findEntityNode(String entityName) {
        for (MindMapSubgraph sg : store.listSubgraphs(tenantId)) {
            if (SubgraphTypes.GOAL.equals(sg.type())) {continue;}
            MindMapNode resolved = store.resolveNode(entityName, sg.id(), tenantId);
            if (resolved != null) {return resolved;}
        }
        return null;
    }

    private int shortestPathToGoal(MindMapNode entityNode, List<MindMapNode> goals) {
        Set<String> goalIds = new HashSet<>();
        for (MindMapNode g : goals) goalIds.add(g.id());

        if (goalIds.contains(entityNode.id())) return 0;

        Set<String> visited = new HashSet<>();
        visited.add(entityNode.id());
        Set<String> currentLevel = Set.of(entityNode.id());

        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            Set<String> nextLevel = new HashSet<>();
            for (String nodeId : currentLevel) {
                List<MindMapEdge> edges = store.neighbors(nodeId, tenantId);
                for (MindMapEdge edge : edges) {
                    String neighbor = edge.sourceNodeId().equals(nodeId)
                            ? edge.targetNodeId() : edge.sourceNodeId();
                    if (goalIds.contains(neighbor)) return depth;
                    if (visited.add(neighbor)) nextLevel.add(neighbor);
                }
            }
            currentLevel = nextLevel;
            if (currentLevel.isEmpty()) break;
        }
        return -1;
    }

    private String findGoalSubgraph() {
        for (MindMapSubgraph sg : store.listSubgraphs(tenantId)) {
            if (SubgraphTypes.GOAL.equals(sg.type())) return sg.id();
        }
        return null;
    }
}

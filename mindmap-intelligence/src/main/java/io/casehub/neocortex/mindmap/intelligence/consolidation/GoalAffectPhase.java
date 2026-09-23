package io.casehub.neocortex.mindmap.intelligence.consolidation;

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
import java.util.logging.Logger;

@ApplicationScoped
@Priority(37)
public class GoalAffectPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(GoalAffectPhase.class.getName());

    private final MindMapStore store;

    @Inject
    public GoalAffectPhase(Instance<MindMapStore> store) {
        this.store = store.isResolvable() ? store.get() : null;
    }

    public GoalAffectPhase(MindMapStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "goal-affect";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (store == null) return;

        String goalSgId = findGoalSubgraph(tenantId);
        if (goalSgId == null) return;

        for (MindMapNode node : store.nodesIn(goalSgId, tenantId)) {
            String status = node.property("status").orElse("active");
            double urgency = node.property("urgency").map(Double::parseDouble).orElse(0.0);

            Double pleasure = null;
            Double arousal = null;
            Double dominance = null;

            switch (status) {
                case "active" -> {
                    arousal = urgency * 0.8;
                    pleasure = 0.2;
                    dominance = 0.3;
                }
                case "blocked" -> {
                    pleasure = -0.4 - (urgency * 0.4);
                    arousal = 0.3 + (urgency * 0.5);
                    dominance = -0.3;
                }
                case "completed" -> {
                    pleasure = 0.6;
                    arousal = 0.2;
                    dominance = 0.5;
                }
                case "dormant" -> {
                    pleasure = 0.0;
                    arousal = -0.3;
                    dominance = 0.0;
                }
                case "abandoned" -> {
                    pleasure = -0.2;
                    arousal = -0.2;
                    dominance = -0.1;
                }
                default -> {}
            }

            if (pleasure != null) {
                store.updateNode(node.id(),
                        NodeUpdate.empty().withPad(pleasure, arousal, dominance),
                        tenantId);
            }
        }
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

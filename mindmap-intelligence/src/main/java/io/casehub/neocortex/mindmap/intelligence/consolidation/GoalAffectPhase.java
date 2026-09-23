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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(37)
public class GoalAffectPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(GoalAffectPhase.class.getName());

    private final MindMapStore store;
    private final Clock        clock;

    @Inject
    public GoalAffectPhase(Instance<MindMapStore> store) {
        this.store = store.isResolvable() ? store.get() : null;
        this.clock = Clock.systemUTC();
    }

    public GoalAffectPhase(MindMapStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public GoalAffectPhase(MindMapStore store) {
        this(store, Clock.systemUTC());
    }

    @Override
    public String name() {
        return "goal-affect";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (store == null) {return;}

        String goalSgId = findGoalSubgraph(tenantId);
        if (goalSgId == null) {return;}

        Instant now = clock.instant();

        for (MindMapNode node : store.nodesIn(goalSgId, tenantId)) {
            String status  = node.property("status").orElse("active");
            double urgency = GoalUrgency.computeUrgency(node, now);

            Double pleasure  = null;
            Double arousal   = null;
            Double dominance = null;

            switch (status) {
                case "active" -> {
                    arousal   = urgency * 0.8;
                    pleasure  = 0.2;
                    dominance = 0.3;
                }
                case "blocked" -> {
                    pleasure  = -0.4 - (urgency * 0.4);
                    arousal   = 0.3 + (urgency * 0.5);
                    dominance = -0.3;
                }
                case "completed" -> {
                    pleasure  = 0.6;
                    arousal   = 0.2;
                    dominance = 0.5;
                }
                case "dormant" -> {
                    pleasure  = 0.0;
                    arousal   = -0.3;
                    dominance = 0.0;
                }
                case "abandoned" -> {
                    pleasure  = -0.2;
                    arousal   = -0.2;
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

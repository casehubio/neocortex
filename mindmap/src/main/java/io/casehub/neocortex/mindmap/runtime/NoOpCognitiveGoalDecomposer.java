package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.CognitiveGoalDecomposer;
import io.casehub.neocortex.mindmap.GoalDecompositionResult;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpCognitiveGoalDecomposer implements CognitiveGoalDecomposer {

    @Override
    public GoalDecompositionResult decompose(String goalDescription,
                                             List<MindMapNode> contextNodes,
                                             String tenantId) {
        return GoalDecompositionResult.EMPTY;
    }
}

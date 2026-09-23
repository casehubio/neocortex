package io.casehub.neocortex.mindmap;

import java.util.List;

@FunctionalInterface
public interface CognitiveGoalDecomposer {
    GoalDecompositionResult decompose(
            String goalDescription,
            List<MindMapNode> contextNodes,
            String tenantId);
}

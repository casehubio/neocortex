package io.casehub.neocortex.mindmap;

import java.util.List;

@FunctionalInterface
public interface CognitiveGoalRecognizer {
    List<RecognizedGoal> recognize(
            String conversationText,
            List<MindMapNode> existingGoals,
            String tenantId);
}

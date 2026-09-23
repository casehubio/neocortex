package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.CognitiveGoalRecognizer;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.RecognizedGoal;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpCognitiveGoalRecognizer implements CognitiveGoalRecognizer {

    @Override
    public List<RecognizedGoal> recognize(String conversationText,
                                          List<MindMapNode> existingGoals,
                                          String tenantId) {
        return List.of();
    }
}

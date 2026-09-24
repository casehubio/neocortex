package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.CognitiveEmotion;

import java.util.List;

@FunctionalInterface
public interface GoalAppraisal {
    List<CognitiveEmotion> appraise(MindMapNode goal, AppraisalContext context);
}

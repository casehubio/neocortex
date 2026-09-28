package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.CognitiveEmotion;

import java.util.List;

@FunctionalInterface
public interface ActionAppraisal {
    List<CognitiveEmotion> appraise(ActionContext context);
}

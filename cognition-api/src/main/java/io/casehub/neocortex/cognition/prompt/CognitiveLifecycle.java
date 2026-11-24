package io.casehub.neocortex.cognition.prompt;

import org.jspecify.annotations.Nullable;

import java.util.Set;

public interface CognitiveLifecycle extends CognitivePromptSource {

    void tick(String agentId, String tenantId, Set<String> activeSubjects);

    default @Nullable String evaluateProactive(String agentId, String tenantId,
                                                String channelContext) {
        return null;
    }

    void recordInteraction(String agentId, String tenantId,
                           @Nullable String subjectId,
                           String userMessage, String response);
}

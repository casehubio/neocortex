package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import java.util.Objects;
import java.util.Set;

public record ModulationContext(
        String agentId,
        String tenantId,
        MoodState currentMood,
        PersonalityWeights personalityWeights,
        Set<String> contextEntityNodeIds) {

    public ModulationContext {
        Objects.requireNonNull(tenantId, "tenantId required");
        contextEntityNodeIds = contextEntityNodeIds != null
                ? Set.copyOf(contextEntityNodeIds) : Set.of();
    }
}

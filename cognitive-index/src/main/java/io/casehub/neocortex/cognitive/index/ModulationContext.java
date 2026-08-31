package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.memory.personality.PersonalityWeights;

import java.time.Instant;
import java.util.Objects;

public record ModulationContext(
    MoodState currentMood,
    PersonalityWeights personality,
    Instant now
) {
    public ModulationContext {
        Objects.requireNonNull(now, "now");
    }

    public static ModulationContext of(Instant now) {
        return new ModulationContext(null, null, now);
    }

    public ModulationContext withMood(MoodState mood) {
        return new ModulationContext(mood, personality, now);
    }

    public ModulationContext withPersonality(PersonalityWeights weights) {
        return new ModulationContext(currentMood, weights, now);
    }
}

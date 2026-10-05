package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.mood.MoodState;
import java.util.List;
import java.util.Objects;

public record SalienceContext(
        String observation,
        List<Drive> drives,
        MoodState moodState,
        List<Memory> activeConcerns,
        List<Memory> recentExperiences) {
    public SalienceContext {
        Objects.requireNonNull(observation, "observation");
        drives = drives != null ? List.copyOf(drives) : List.of();
        activeConcerns = activeConcerns != null ? List.copyOf(activeConcerns) : List.of();
        recentExperiences = recentExperiences != null ? List.copyOf(recentExperiences) : List.of();
    }
}

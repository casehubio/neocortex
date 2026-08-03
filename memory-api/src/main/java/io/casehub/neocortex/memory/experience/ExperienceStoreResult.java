package io.casehub.neocortex.memory.experience;

import java.util.List;

public record ExperienceStoreResult(List<String> stored, List<ExperienceStoreFailure> failures) {
    public ExperienceStoreResult {
        stored = List.copyOf(stored);
        failures = List.copyOf(failures);
    }

    public static ExperienceStoreResult empty() {
        return new ExperienceStoreResult(List.of(), List.of());
    }

    public boolean allSucceeded() {
        return failures.isEmpty();
    }
}

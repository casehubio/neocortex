package io.casehub.neocortex.memory.engagement;

import java.util.List;

public record EngagementStoreResult(List<String> stored, List<EngagementStoreFailure> failures) {
    public EngagementStoreResult {
        stored = List.copyOf(stored);
        failures = List.copyOf(failures);
    }

    public static EngagementStoreResult empty() {
        return new EngagementStoreResult(List.of(), List.of());
    }

    public boolean allSucceeded() {
        return failures.isEmpty();
    }
}

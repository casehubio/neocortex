package io.casehub.neocortex.memory.mood;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record MoodState(
        String agentId,
        String tenantId,
        Instant timestamp, double pleasure,
        double arousal,
        double dominance,
        String cause,
        String turnId,
        Map<String, String> metadata
) {
    public MoodState {
        if (timestamp == null) timestamp = Instant.now();
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(cause, "cause required");
        if (cause.isBlank()) throw new IllegalArgumentException("cause must not be blank");
        validateAxis("pleasure", pleasure);
        validateAxis("arousal", arousal);
        validateAxis("dominance", dominance);
        Objects.requireNonNull(metadata, "metadata required");
        metadata = Map.copyOf(metadata);
    }

    private static void validateAxis(String name, double value) {
        if (value < -1.0 || value > 1.0)
            throw new IllegalArgumentException(name + " must be in [-1, 1], got " + value);
    }
}

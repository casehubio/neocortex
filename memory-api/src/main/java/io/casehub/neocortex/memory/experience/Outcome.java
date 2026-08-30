package io.casehub.neocortex.memory.experience;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record Outcome(
        String agentId,
        String tenantId,
        String caseId,
        String turnId,
        Instant timestamp, String description,
        Double confidence,
        Map<String, String> metadata,
        String result,
        String capability
) implements ExperienceEvent {
    public Outcome {
        if (timestamp == null) timestamp = Instant.now();
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(description, "description required");
        if (description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        Objects.requireNonNull(metadata, "metadata required");
        metadata = Map.copyOf(metadata);
        Objects.requireNonNull(result, "Outcome.result required");
        if (result.isBlank()) throw new IllegalArgumentException("Outcome.result must not be blank");
        if (confidence != null && (confidence < 0.0 || confidence > 1.0))
            throw new IllegalArgumentException("confidence must be in [0, 1], got " + confidence);
    }
}

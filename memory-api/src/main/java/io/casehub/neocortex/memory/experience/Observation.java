package io.casehub.neocortex.memory.experience;

import java.util.Map;
import java.util.Objects;

public record Observation(
    String agentId,
    String tenantId,
    String caseId,
    String turnId,
    String description,
    Double importance,
    Map<String, String> metadata,
    String subject
) implements ExperienceEvent {
    public Observation {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(description, "description required");
        if (description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        Objects.requireNonNull(metadata, "metadata required");
        metadata = Map.copyOf(metadata);
        Objects.requireNonNull(subject, "Observation.subject required");
        if (subject.isBlank()) throw new IllegalArgumentException("Observation.subject must not be blank");
        if (importance != null && (importance < 0.0 || importance > 1.0))
            throw new IllegalArgumentException("importance must be in [0, 1], got " + importance);
    }
}

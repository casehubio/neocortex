package io.casehub.neocortex.memory.engagement;

import java.util.Map;
import java.util.Objects;

public record EngagementEvent(
    String agentId,
    String otherAgentId,
    String tenantId,
    String caseId,
    String turnId,
    String description,
    Double importance,
    Map<String, String> metadata,
    Boolean responded,
    Long responseTimeMs,
    Integer responseLength,
    Double sentimentShift,
    Integer reactionCount,
    Boolean continued
) {
    public EngagementEvent {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(otherAgentId, "otherAgentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(turnId, "turnId required");
        if (turnId.isBlank()) throw new IllegalArgumentException("turnId must not be blank");
        Objects.requireNonNull(description, "description required");
        if (description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        if (agentId.equals(otherAgentId))
            throw new IllegalArgumentException("agentId and otherAgentId must differ");
        if (sentimentShift != null && (sentimentShift < -1.0 || sentimentShift > 1.0))
            throw new IllegalArgumentException("sentimentShift must be in [-1, 1], got " + sentimentShift);
        if (importance != null && (importance < 0.0 || importance > 1.0))
            throw new IllegalArgumentException("importance must be in [0, 1], got " + importance);
        Objects.requireNonNull(metadata, "metadata required");
        metadata = Map.copyOf(metadata);
    }
}

package io.casehub.neocortex.memory.relationship;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record RelationshipEvent(
        String agentId,
        String otherAgentId,
        String tenantId,
        String caseId,
        String turnId,
        Instant timestamp, String sourceEventType,
        QualitySignal qualitySignal,
        String description,
        Double confidence,
        Map<String, String> metadata
) {
    public RelationshipEvent {
        if (timestamp == null) timestamp = Instant.now();
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(otherAgentId, "otherAgentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(sourceEventType, "sourceEventType required");
        if (sourceEventType.isBlank()) throw new IllegalArgumentException("sourceEventType must not be blank");
        Objects.requireNonNull(qualitySignal, "qualitySignal required");
        Objects.requireNonNull(description, "description required");
        if (description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        if (agentId.equals(otherAgentId))
            throw new IllegalArgumentException("agentId and otherAgentId must differ — self-referential relationships are not tracked");
        Objects.requireNonNull(metadata, "metadata required");
        metadata = Map.copyOf(metadata);
        if (confidence != null && (confidence < 0.0 || confidence > 1.0))
            throw new IllegalArgumentException("confidence must be in [0, 1], got " + confidence);
    }
}

package io.casehub.neocortex.memory.experience;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FormativeExperience(
        String agentId,
        String tenantId,
        String caseId,
        String turnId,
        Instant timestamp,
        String description,
        Double confidence,
        Map<String, String> metadata,
        String catalogueEntryId,
        List<String> situationTypes,
        double salienceMultiplier,
        String reinforcementSchedule,
        String developmentalPeriod,
        Double pleasure,
        Double arousal,
        Double dominance
) implements ExperienceEvent {
    public FormativeExperience {
        if (timestamp == null) timestamp = Instant.now();
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(description, "description required");
        if (description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        Objects.requireNonNull(metadata, "metadata required");
        metadata = Map.copyOf(metadata);
        Objects.requireNonNull(catalogueEntryId, "catalogueEntryId required");
        if (catalogueEntryId.isBlank()) throw new IllegalArgumentException("catalogueEntryId must not be blank");
        Objects.requireNonNull(situationTypes, "situationTypes required");
        if (situationTypes.isEmpty()) throw new IllegalArgumentException("situationTypes must not be empty");
        situationTypes = List.copyOf(situationTypes);
        if (confidence != null && (confidence < 0.0 || confidence > 1.0))
            throw new IllegalArgumentException("confidence must be in [0, 1], got " + confidence);
    }
}

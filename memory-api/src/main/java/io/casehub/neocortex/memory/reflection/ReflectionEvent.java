package io.casehub.neocortex.memory.reflection;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ReflectionEvent(
    String agentId,
    String tenantId,
    String caseId,
    String insight,
    int level,
    List<String> sourceMemoryIds,
    Double confidence,
    Map<String, String> metadata
) {
    public ReflectionEvent {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(insight, "insight required");
        if (insight.isBlank()) throw new IllegalArgumentException("insight must not be blank");
        if (level < 1) throw new IllegalArgumentException("level must be >= 1, got " + level);
        Objects.requireNonNull(sourceMemoryIds, "sourceMemoryIds required");
        if (sourceMemoryIds.isEmpty()) throw new IllegalArgumentException("sourceMemoryIds must not be empty");
        sourceMemoryIds = List.copyOf(sourceMemoryIds);
        Objects.requireNonNull(metadata, "metadata required");
        metadata = Map.copyOf(metadata);
        if (confidence != null && (confidence < 0.0 || confidence > 1.0))
            throw new IllegalArgumentException("confidence must be in [0, 1], got " + confidence);
    }
}

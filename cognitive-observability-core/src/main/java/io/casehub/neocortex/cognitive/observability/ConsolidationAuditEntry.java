package io.casehub.neocortex.cognitive.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record ConsolidationAuditEntry(
    String tenantId,
    Instant startedAt,
    Instant completedAt,
    List<PhaseAuditEntry> phases
) {
    public record PhaseAuditEntry(
        String phaseName,
        Duration duration,
        int mutationCount,
        boolean success,
        String errorMessage
    ) {}
}

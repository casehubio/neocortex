package io.casehub.neocortex.memory.cbr;

import java.time.Instant;
import java.util.Objects;

public record CbrOutcomeRecorded(
    String tenantId,
    String caseId,
    CbrOutcome outcome,
    Instant recordedAt
) {
    public CbrOutcomeRecorded {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }
}

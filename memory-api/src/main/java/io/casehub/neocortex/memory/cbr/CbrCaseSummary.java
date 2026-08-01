package io.casehub.neocortex.memory.cbr;

import java.time.Instant;

public record CbrCaseSummary(
    String caseId, String entityId, String caseType,
    String producerAgentId, Double trustScore, Instant storedAt
) {}

package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.memory.MemoryDomain;
import java.util.Objects;

public record CbrScanRequest(
    String tenantId, MemoryDomain domain, String caseType,
    int limit, String afterCaseId
) {
    public CbrScanRequest {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(domain, "domain required");
        Objects.requireNonNull(caseType, "caseType required");
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
    }
}

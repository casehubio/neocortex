package io.casehub.neocortex.memory;

import java.util.Objects;

public record EraseRequest(
        Subject subject,
        MemoryDomain domain,
        String tenantId,
        String caseId
) {
    public EraseRequest {
        Objects.requireNonNull(subject, "subject required");
        Objects.requireNonNull(domain, "domain required");
        Objects.requireNonNull(tenantId, "tenantId required");
    }

    @Deprecated(forRemoval = true)
    public EraseRequest(String entityId, MemoryDomain domain, String tenantId, String caseId) {
        this(Subject.of("unknown", entityId), domain, tenantId, caseId);
    }

    @Deprecated(forRemoval = true)
    public String entityId() {
        return subject.id();
    }
}

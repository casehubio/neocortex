package io.casehub.neocortex.memory;

public record MemoryScanRequest(
        String tenantId,
        String domain,
        String attributeKey,
        String attributeValue,
        int limit,
        String afterMemoryId,
        io.casehub.platform.api.identity.PrincipalId callerPrincipalId
) {
    public MemoryScanRequest {
        java.util.Objects.requireNonNull(tenantId, "tenantId required");
        if (limit < 1) {throw new IllegalArgumentException("limit must be >= 1, got: " + limit);}
        if (attributeValue != null && attributeKey == null) {
            throw new IllegalArgumentException("attributeValue requires attributeKey");
        }
        if (attributeKey != null && attributeValue == null) {
            throw new IllegalArgumentException("attributeKey requires attributeValue for filtered scan");
        }
    }

    public MemoryScanRequest(String tenantId, String domain, String attributeKey,
                             String attributeValue, int limit, String afterMemoryId) {
        this(tenantId, domain, attributeKey, attributeValue, limit, afterMemoryId, null);
    }
}

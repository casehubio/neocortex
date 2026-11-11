package io.casehub.neocortex.caps;

import java.util.Objects;

public record ClassificationContext(String tenantId, String agentId) {

    public static final ClassificationContext NONE = new ClassificationContext((String) null, null);

    public ClassificationContext(String tenantId) {
        this(tenantId, null);
    }

    public static ClassificationContext of(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        return new ClassificationContext(tenantId, null);
    }

    public static ClassificationContext of(String tenantId, String agentId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        return new ClassificationContext(tenantId, agentId);
    }

    public boolean isResolvable() {
        return tenantId != null;
    }
}

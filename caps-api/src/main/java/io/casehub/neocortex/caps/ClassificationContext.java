package io.casehub.neocortex.caps;

import java.util.Objects;

public record ClassificationContext(String tenantId) {

    public static final ClassificationContext NONE = new ClassificationContext((String) null);

    public static ClassificationContext of(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        return new ClassificationContext(tenantId);
    }

    public boolean isResolvable() {
        return tenantId != null;
    }
}

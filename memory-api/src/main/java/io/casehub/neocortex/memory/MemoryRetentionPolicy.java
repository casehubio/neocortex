package io.casehub.neocortex.memory;

import java.util.Objects;

public record MemoryRetentionPolicy(
    String tenantId,
    MemoryDomain domain,
    Integer maxAgeDays,
    Double minImportance
) {
    public MemoryRetentionPolicy {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(domain, "domain required");
        if (maxAgeDays == null && minImportance == null) {
            throw new IllegalArgumentException(
                "at least one of maxAgeDays or minImportance must be non-null");
        }
        if (maxAgeDays != null && maxAgeDays <= 0) {
            throw new IllegalArgumentException("maxAgeDays must be positive, got " + maxAgeDays);
        }
        if (minImportance != null && (minImportance < 0.0 || minImportance > 1.0)) {
            throw new IllegalArgumentException(
                "minImportance must be in [0, 1], got " + minImportance);
        }
    }
}

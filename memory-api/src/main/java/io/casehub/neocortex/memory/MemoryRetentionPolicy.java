package io.casehub.neocortex.memory;

import java.util.Objects;

public record MemoryRetentionPolicy(
        String tenantId,
        MemoryDomain domain,
        Integer maxAgeDays,
        Double minConfidence
) {
    public MemoryRetentionPolicy {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(domain, "domain required");
        if (maxAgeDays == null && minConfidence == null) {
            throw new IllegalArgumentException(
                    "at least one of maxAgeDays or minConfidence must be non-null");
        }
        if (maxAgeDays != null && maxAgeDays <= 0) {
            throw new IllegalArgumentException("maxAgeDays must be positive, got " + maxAgeDays);
        }
        if (minConfidence != null && !(minConfidence >= 0.0 && minConfidence <= 1.0)) {
            throw new IllegalArgumentException(
                    "minConfidence must be in [0, 1], got " + minConfidence);
        }
    }
}

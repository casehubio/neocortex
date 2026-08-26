package io.casehub.neocortex.memory;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public sealed interface MemoryEntityErased {
    String tenantId();
    int erasedCount();
    Instant erasedAt();

    record ByRequest(String tenantId, int erasedCount,
                     String entityId, MemoryDomain domain,
                     Instant erasedAt) implements MemoryEntityErased {
        public ByRequest {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(domain, "domain");
            Objects.requireNonNull(erasedAt, "erasedAt");
        }
    }

    record ByEntity(String tenantId, int erasedCount,
                    String entityId,
                    Instant erasedAt) implements MemoryEntityErased {
        public ByEntity {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(erasedAt, "erasedAt");
        }
    }

    record CrossTenant(int erasedCount,
                       String entityId, Set<String> tenantIds,
                       Instant erasedAt) implements MemoryEntityErased {
        public CrossTenant {
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(tenantIds, "tenantIds");
            Objects.requireNonNull(erasedAt, "erasedAt");
            tenantIds = Set.copyOf(tenantIds);
        }

        @Override
        public String tenantId() {
            return null;
        }
    }
}

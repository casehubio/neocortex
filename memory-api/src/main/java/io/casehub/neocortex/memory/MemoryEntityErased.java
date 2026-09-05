package io.casehub.neocortex.memory;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public sealed interface MemoryEntityErased {
    String tenantId();

    int erasedCount();

    Instant erasedAt();

    record ByRequest(String tenantId, int erasedCount,
                     Subject subject, MemoryDomain domain,
                     Instant erasedAt) implements MemoryEntityErased {
        public ByRequest {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(domain, "domain");
            Objects.requireNonNull(erasedAt, "erasedAt");
        }

        @Deprecated(forRemoval = true)
        public ByRequest(String tenantId, int erasedCount,
                         String entityId, MemoryDomain domain, Instant erasedAt) {
            this(tenantId, erasedCount, Subject.of("unknown", entityId), domain, erasedAt);
        }

        @Deprecated(forRemoval = true)
        public String entityId() {
            return subject.id();
        }
    }

    record ByEntity(String tenantId, int erasedCount,
                    Subject subject,
                    Instant erasedAt) implements MemoryEntityErased {
        public ByEntity {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(erasedAt, "erasedAt");
        }

        @Deprecated(forRemoval = true)
        public ByEntity(String tenantId, int erasedCount,
                        String entityId, Instant erasedAt) {
            this(tenantId, erasedCount, Subject.of("unknown", entityId), erasedAt);
        }

        @Deprecated(forRemoval = true)
        public String entityId() {
            return subject.id();
        }
    }

    record CrossTenant(int erasedCount,
                       Subject subject, Set<String> tenantIds,
                       Instant erasedAt) implements MemoryEntityErased {
        public CrossTenant {
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(tenantIds, "tenantIds");
            Objects.requireNonNull(erasedAt, "erasedAt");
            tenantIds = Set.copyOf(tenantIds);
        }

        @Deprecated(forRemoval = true)
        public CrossTenant(int erasedCount, String entityId,
                           Set<String> tenantIds, Instant erasedAt) {
            this(erasedCount, Subject.of("unknown", entityId), tenantIds, erasedAt);
        }

        @Deprecated(forRemoval = true)
        public String entityId() {
            return subject.id();
        }

        @Override
        public String tenantId() {
            return null;
        }
    }
}

package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.platform.api.path.Path;

import java.time.Instant;
import java.util.Objects;

public sealed interface CbrCasesErased {
    String tenantId();

    int erasedCount();

    Instant erasedAt();

    record ByRequest(String tenantId, int erasedCount,
                     io.casehub.neocortex.memory.Subject subject, MemoryDomain domain, String caseId,
                     Instant erasedAt) implements CbrCasesErased {
        public ByRequest {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(domain, "domain");
            Objects.requireNonNull(erasedAt, "erasedAt");
        }

        @Deprecated(forRemoval = true)
        public ByRequest(String tenantId, int erasedCount,
                         String entityId, MemoryDomain domain, String caseId, Instant erasedAt) {
            this(tenantId, erasedCount, io.casehub.neocortex.memory.Subject.of("unknown", entityId), domain, caseId, erasedAt);
        }

        @Deprecated(forRemoval = true)
        public String entityId() {
            return subject.id();
        }
    }

    record ByEntity(String tenantId, int erasedCount,
                    io.casehub.neocortex.memory.Subject subject,
                    Instant erasedAt) implements CbrCasesErased {
        public ByEntity {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(erasedAt, "erasedAt");
        }

        @Deprecated(forRemoval = true)
        public ByEntity(String tenantId, int erasedCount,
                        String entityId, Instant erasedAt) {
            this(tenantId, erasedCount, io.casehub.neocortex.memory.Subject.of("unknown", entityId), erasedAt);
        }

        @Deprecated(forRemoval = true)
        public String entityId() {
            return subject.id();
        }
    }

    record ByScope(String tenantId, int erasedCount,
                   Path scope,
                   Instant erasedAt) implements CbrCasesErased {
        public ByScope {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(erasedAt, "erasedAt");
        }
    }
}

package io.casehub.neocortex.memory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record MemoryQuery(
        List<Subject> subjects,
        MemoryDomain domain,
        String tenantId,
        String caseId,
        String question,
        int limit,
        Instant since,
        MemoryOrder order,
        String callerPrincipalId
) {
    public static final int MAX_SUBJECTS = 25;

    @Deprecated(forRemoval = true)
    public static final int MAX_ENTITY_IDS = MAX_SUBJECTS;

    public MemoryQuery {
        Objects.requireNonNull(subjects, "subjects required");
        Objects.requireNonNull(domain, "domain required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(order, "order required");
        if (subjects.isEmpty()) {throw new IllegalArgumentException("subjects must not be empty");}
        if (subjects.size() > MAX_SUBJECTS) {
            throw new IllegalArgumentException("subjects must not exceed " + MAX_SUBJECTS + ", got: " + subjects.size());
        }
        if (limit < 1) {throw new IllegalArgumentException("limit must be >= 1, got: " + limit);}
        subjects = List.copyOf(subjects);
    }

    @Deprecated(forRemoval = true)
    public MemoryQuery(List<String> entityIds, MemoryDomain domain, String tenantId,
                       String caseId, String question, int limit, Instant since, MemoryOrder order) {
        this(entityIds.stream().map(id -> Subject.of("unknown", id)).toList(),
             domain, tenantId, caseId, question, limit, since, order, null);
    }

    public static MemoryQuery forSubject(Subject subject, MemoryDomain domain, String tenantId) {
        return new MemoryQuery(List.of(subject), domain, tenantId, null, null, 20, null, MemoryOrder.CHRONOLOGICAL, null);
    }

    public static MemoryQuery forSubjects(List<Subject> subjects, MemoryDomain domain, String tenantId) {
        return new MemoryQuery(subjects, domain, tenantId, null, null, 20, null, MemoryOrder.CHRONOLOGICAL, null);
    }

    @Deprecated(forRemoval = true)
    public static MemoryQuery forEntity(String entityId, MemoryDomain domain, String tenantId) {
        return forSubject(Subject.of("unknown", entityId), domain, tenantId);
    }

    @Deprecated(forRemoval = true)
    public static MemoryQuery forEntities(List<String> entityIds, MemoryDomain domain, String tenantId) {
        return forSubjects(entityIds.stream().map(id -> Subject.of("unknown", id)).toList(), domain, tenantId);
    }

    @Deprecated(forRemoval = true)
    public List<String> entityIds() {
        return subjects.stream().map(Subject::id).toList();
    }

    public MemoryQuery withCaseId(String caseId) {
        return new MemoryQuery(subjects, domain, tenantId, caseId, question, limit, since, order, callerPrincipalId);
    }

    public MemoryQuery withQuestion(String question) {
        return new MemoryQuery(subjects, domain, tenantId, caseId, question, limit, since, order, callerPrincipalId);
    }

    public MemoryQuery withLimit(int limit) {
        return new MemoryQuery(subjects, domain, tenantId, caseId, question, limit, since, order, callerPrincipalId);
    }

    public MemoryQuery withSince(Instant since) {
        return new MemoryQuery(subjects, domain, tenantId, caseId, question, limit, since, order, callerPrincipalId);
    }

    public MemoryQuery withOrder(MemoryOrder order) {
        return new MemoryQuery(subjects, domain, tenantId, caseId, question, limit, since, order, callerPrincipalId);
    }

    public MemoryQuery withCallerPrincipalId(String callerPrincipalId) {
        return new MemoryQuery(subjects, domain, tenantId, caseId, question, limit, since, order, callerPrincipalId);
    }
}

package io.casehub.neocortex.memory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record GraphMemoryQuery(
        String tenantId,
        List<Subject> subjects,
        MemoryDomain domain,
        String question,
        int limit,
        Instant since,
        Instant validAt,
        Set<String> subjectTypes,
        MemoryResultType resultType,
        String callerPrincipalId
) {
    public static final int MAX_SUBJECTS = 25;

    @Deprecated(forRemoval = true)
    public static final int MAX_ENTITY_IDS = MAX_SUBJECTS;

    public GraphMemoryQuery {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(subjects, "subjects required");
        Objects.requireNonNull(domain, "domain required");
        Objects.requireNonNull(question, "question required — for chronological retrieval use query(MemoryQuery)");
        if (question.isBlank()) {throw new IllegalArgumentException("question must not be blank");}
        if (subjects.isEmpty()) {throw new IllegalArgumentException("subjects must not be empty");}
        if (subjects.size() > MAX_SUBJECTS) {
            throw new IllegalArgumentException("subjects must not exceed " + MAX_SUBJECTS);
        }
        if (limit < 1) {throw new IllegalArgumentException("limit must be >= 1, got: " + limit);}
        subjects     = List.copyOf(subjects);
        subjectTypes = subjectTypes == null ? null : Set.copyOf(subjectTypes);
        resultType   = resultType == null ? MemoryResultType.DEFAULT : resultType;
    }

    @Deprecated(forRemoval = true)
    public GraphMemoryQuery(String tenantId, List<String> entityIds, MemoryDomain domain,
                            String question, int limit, Instant since, Instant validAt,
                            Set<String> entityTypes, MemoryResultType resultType) {
        this(tenantId, entityIds.stream().map(id -> Subject.of("unknown", id)).toList(),
             domain, question, limit, since, validAt, entityTypes, resultType, null);
    }

    public static GraphMemoryQuery forSubject(
            final Subject subject,
            final MemoryDomain domain,
            final String tenantId,
            final String question) {
        return new GraphMemoryQuery(tenantId, List.of(subject), domain, question,
                                    10, null, null, null, MemoryResultType.DEFAULT, null);
    }

    @Deprecated(forRemoval = true)
    public static GraphMemoryQuery forEntity(
            final String entityId,
            final MemoryDomain domain,
            final String tenantId,
            final String question) {
        return forSubject(Subject.of("unknown", entityId), domain, tenantId, question);
    }

    @Deprecated(forRemoval = true)
    public List<String> entityIds() {
        return subjects.stream().map(Subject::id).toList();
    }

    @Deprecated(forRemoval = true)
    public Set<String> entityTypes() {
        return subjectTypes;
    }

    public GraphMemoryQuery withLimit(final int limit) {
        return new GraphMemoryQuery(tenantId, subjects, domain, question,
                                    limit, since, validAt, subjectTypes, resultType, callerPrincipalId);
    }

    public GraphMemoryQuery withSince(final Instant since) {
        return new GraphMemoryQuery(tenantId, subjects, domain, question,
                                    limit, since, validAt, subjectTypes, resultType, callerPrincipalId);
    }

    public GraphMemoryQuery withValidAt(final Instant validAt) {
        return new GraphMemoryQuery(tenantId, subjects, domain, question,
                                    limit, since, validAt, subjectTypes, resultType, callerPrincipalId);
    }

    public GraphMemoryQuery withSubjectTypes(final Set<String> subjectTypes) {
        return new GraphMemoryQuery(tenantId, subjects, domain, question,
                                    limit, since, validAt, subjectTypes, resultType, callerPrincipalId);
    }

    @Deprecated(forRemoval = true)
    public GraphMemoryQuery withEntityTypes(final Set<String> entityTypes) {
        return withSubjectTypes(entityTypes);
    }

    public GraphMemoryQuery withResultType(final MemoryResultType resultType) {
        return new GraphMemoryQuery(tenantId, subjects, domain, question,
                                    limit, since, validAt, subjectTypes, resultType, callerPrincipalId);
    }

    public GraphMemoryQuery withCallerPrincipalId(final String callerPrincipalId) {
        return new GraphMemoryQuery(tenantId, subjects, domain, question,
                                    limit, since, validAt, subjectTypes, resultType, callerPrincipalId);
    }
}

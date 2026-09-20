package io.casehub.neocortex.memory.spring.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryCapability;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryPermissions;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.MemoryRetentionPolicy;
import io.casehub.neocortex.memory.MemoryScanRequest;
import io.casehub.neocortex.memory.StoreAllResult;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.jpa.MemoryEntry;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.PrincipalId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpringMemoryStore implements CaseMemoryStore {

    private final MemoryEntryRepository repository;
    private final CurrentPrincipal principal;
    private final ObjectMapper objectMapper;
    private final boolean ftsEnabled;
    private final String ftsLanguage;

    @PersistenceContext
    private EntityManager em;

    public SpringMemoryStore(MemoryEntryRepository repository, CurrentPrincipal principal,
                             ObjectMapper objectMapper, boolean ftsEnabled, String ftsLanguage) {
        this.repository = repository;
        this.principal = principal;
        this.objectMapper = objectMapper;
        this.ftsEnabled = ftsEnabled;
        this.ftsLanguage = ftsLanguage;
    }

    @Override
    public Set<MemoryCapability> capabilities() {
        return Set.of(
                MemoryCapability.CHRONOLOGICAL_ORDER,
                MemoryCapability.DOMAIN_SCOPED,
                MemoryCapability.CASE_SCOPED,
                MemoryCapability.SINCE_FILTER,
                MemoryCapability.BATCH_STORE,
                MemoryCapability.FULL_TEXT_SEARCH,
                MemoryCapability.ERASE_BY_ID,
                MemoryCapability.ERASE_ENTITY,
                MemoryCapability.ERASE_DOMAIN_CASE,
                MemoryCapability.CROSS_TENANT_ERASE,
                MemoryCapability.SCAN,
                MemoryCapability.DISCOVER_TENANTS,
                MemoryCapability.PURGE
        );
    }

    @Override
    @Transactional
    public String store(MemoryInput input) {
        MemoryPermissions.assertTenant(input.tenantId(), principal);

        MemoryEntry entry = toEntry(input);
        repository.save(entry);
        return entry.memoryId;
    }

    @Override
    @Transactional
    public StoreAllResult storeAll(List<MemoryInput> inputs) {
        if (inputs.isEmpty()) return StoreAllResult.empty();
        var entries = inputs.stream().map(input -> {
            MemoryPermissions.assertTenant(input.tenantId(), principal);
            return toEntry(input);
        }).toList();
        repository.saveAll(entries);
        return new StoreAllResult(entries.stream().map(e -> e.memoryId).toList(), List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Memory> query(MemoryQuery query) {
        MemoryPermissions.assertTenant(query.tenantId(), principal);

        if (ftsEnabled && query.order() == MemoryOrder.RELEVANCE && query.question() != null) {
            return queryFts(query);
        }
        return queryChronological(query);
    }

    private List<Memory> queryChronological(MemoryQuery query) {
        var jpql = new StringBuilder(
                "FROM MemoryEntry WHERE tenantId = :tenantId AND entityId IN (:entityIds) AND domain = :domain");
        if (query.caseId() != null) jpql.append(" AND caseId = :caseId");
        if (query.since() != null) jpql.append(" AND createdAt >= :since");
        if (query.callerPrincipalId() != null) jpql.append(" AND (principalId IS NULL OR principalId = :callerPid OR sharedWith LIKE :sharedPattern)");
        jpql.append(" ORDER BY createdAt DESC");

        var jq = em.createQuery(jpql.toString(), MemoryEntry.class)
                .setParameter("tenantId", query.tenantId())
                .setParameter("entityIds", query.subjects().stream().map(Subject::id).toList())
                .setParameter("domain", query.domain().name())
                .setMaxResults(query.limit());

        if (query.caseId() != null) jq.setParameter("caseId", query.caseId());
        if (query.since() != null) jq.setParameter("since", query.since());
        if (query.callerPrincipalId() != null) {
            jq.setParameter("callerPid", query.callerPrincipalId().value());
            jq.setParameter("sharedPattern", "%\"" + query.callerPrincipalId().value() + "\"%");
        }

        return jq.getResultList().stream().map(this::toMemory).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Memory> queryFts(MemoryQuery query) {
        var sql = new StringBuilder("""
                SELECT * FROM memory_entry
                WHERE tenant_id = :tenantId AND entity_id IN (:entityIds) AND domain = :domain
                  AND to_tsvector(CAST(:lang AS regconfig), text)
                      @@ websearch_to_tsquery(CAST(:lang AS regconfig), :question)
                """);
        if (query.caseId() != null) sql.append("  AND case_id = :caseId\n");
        if (query.since() != null) sql.append("  AND created_at >= :since\n");
        if (query.callerPrincipalId() != null) sql.append("  AND (principal_id IS NULL OR principal_id = :callerPid OR shared_with LIKE :sharedPattern)\n");
        sql.append("""
                ORDER BY ts_rank(
                    to_tsvector(CAST(:lang AS regconfig), text),
                    websearch_to_tsquery(CAST(:lang AS regconfig), :question)
                ) DESC
                """);

        var nq = em.createNativeQuery(sql.toString(), MemoryEntry.class)
                .setParameter("tenantId", query.tenantId())
                .setParameter("entityIds", query.subjects().stream().map(Subject::id).toList())
                .setParameter("domain", query.domain().name())
                .setParameter("lang", ftsLanguage)
                .setParameter("question", query.question())
                .setMaxResults(query.limit());

        if (query.caseId() != null) nq.setParameter("caseId", query.caseId());
        if (query.since() != null) nq.setParameter("since", query.since());
        if (query.callerPrincipalId() != null) {
            nq.setParameter("callerPid", query.callerPrincipalId().value());
            nq.setParameter("sharedPattern", "%\"" + query.callerPrincipalId().value() + "\"%");
        }

        return ((List<MemoryEntry>) nq.getResultList()).stream().map(this::toMemory).toList();
    }

    @Override
    @Transactional
    public int erase(EraseRequest request) {
        MemoryPermissions.assertTenant(request.tenantId(), principal);
        if (request.caseId() != null) {
            return repository.eraseByDomainEntityCase(
                    request.tenantId(), request.subject().id(), request.domain().name(), request.caseId());
        }
        return repository.eraseByDomainAndEntity(
                request.tenantId(), request.subject().id(), request.domain().name());
    }

    @Override
    @Transactional
    public void eraseById(String memoryId, Subject subject, String tenantId) {
        MemoryPermissions.assertTenant(tenantId, principal);
        repository.eraseById(memoryId, subject.id(), tenantId);
    }

    @Deprecated(forRemoval = true)
    @Override
    @Transactional
    public void eraseById(String memoryId, String entityId, String tenantId) {
        eraseById(memoryId, Subject.of("unknown", entityId), tenantId);
    }

    @Override
    @Transactional
    public int eraseSubject(Subject subject, String tenantId) {
        MemoryPermissions.assertTenant(tenantId, principal);
        return repository.eraseSubject(tenantId, subject.id());
    }

    @Deprecated(forRemoval = true)
    @Override
    @Transactional
    public int eraseEntity(String entityId, String tenantId) {
        return eraseSubject(Subject.of("unknown", entityId), tenantId);
    }

    @Override
    @Transactional
    public int eraseSubjectAcrossTenants(Subject subject, Set<String> tenantIds) {
        MemoryPermissions.assertCrossTenantAdmin(principal);
        if (tenantIds.isEmpty()) return 0;
        return repository.eraseSubjectAcrossTenants(subject.id(), List.copyOf(tenantIds));
    }

    @Deprecated(forRemoval = true)
    @Override
    @Transactional
    public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
        return eraseSubjectAcrossTenants(Subject.of("unknown", entityId), tenantIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Memory> scan(MemoryScanRequest request) {
        MemoryPermissions.assertTenant(request.tenantId(), principal);

        var sql = new StringBuilder("SELECT * FROM memory_entry WHERE tenant_id = :tenantId");
        if (request.domain() != null) sql.append(" AND domain = :domain");
        if (request.attributeKey() != null) {
            if (ftsEnabled) {
                sql.append(" AND attributes::jsonb->>:attrKey = :attrValue");
            } else {
                sql.append(" AND attributes LIKE :attrPattern ESCAPE '\\'");
            }
        }
        if (request.afterMemoryId() != null) sql.append(" AND memory_id > :cursor");
        sql.append(" ORDER BY memory_id ASC");

        @SuppressWarnings("unchecked")
        var nq = em.createNativeQuery(sql.toString(), MemoryEntry.class)
                .setParameter("tenantId", request.tenantId())
                .setMaxResults(request.limit());

        if (request.domain() != null) nq.setParameter("domain", request.domain());
        if (request.attributeKey() != null) {
            if (ftsEnabled) {
                nq.setParameter("attrKey", request.attributeKey());
                nq.setParameter("attrValue", request.attributeValue());
            } else {
                String escapedKey = request.attributeKey().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String escapedValue = request.attributeValue().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%\"" + escapedKey + "\":\"" + escapedValue + "\"%";
                nq.setParameter("attrPattern", pattern);
            }
        }
        if (request.afterMemoryId() != null) nq.setParameter("cursor", request.afterMemoryId());

        return ((List<MemoryEntry>) nq.getResultList()).stream().map(this::toMemory).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> discoverTenants(String attributeKey, String attributeValue) {
        if ((attributeKey == null) != (attributeValue == null)) {
            throw new IllegalArgumentException(
                    "attributeKey and attributeValue must both be null or both be non-null");
        }
        MemoryPermissions.assertCrossTenantAdmin(principal);

        boolean isH2 = !ftsEnabled;
        StringBuilder sql = new StringBuilder("SELECT DISTINCT m.tenant_id FROM memory_entry m WHERE 1=1");
        Map<String, Object> params = new HashMap<>();

        if (attributeKey != null) {
            if (isH2) {
                String escapedKey = attributeKey.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String escapedValue = attributeValue.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%\"" + escapedKey + "\":\"" + escapedValue + "\"%";
                sql.append(" AND m.attributes LIKE :attrPattern ESCAPE '\\'");
                params.put("attrPattern", pattern);
            } else {
                sql.append(" AND m.attributes::jsonb->>:attrKey = :attrValue");
                params.put("attrKey", attributeKey);
                params.put("attrValue", attributeValue);
            }
        }

        var nativeQuery = em.createNativeQuery(sql.toString());
        params.forEach(nativeQuery::setParameter);

        @SuppressWarnings("unchecked")
        List<String> results = nativeQuery.getResultList();
        return Set.copyOf(results);
    }

    @Override
    @Transactional
    public int purge(MemoryRetentionPolicy policy) {
        StringBuilder jpql = new StringBuilder("DELETE FROM MemoryEntry e WHERE e.tenantId = :t AND e.domain = :d");
        Map<String, Object> params = new HashMap<>();
        params.put("t", policy.tenantId());
        params.put("d", policy.domain().name());

        if (policy.maxAgeDays() != null && policy.minConfidence() != null) {
            jpql.append(" AND e.createdAt < :cutoff AND e.confidence IS NOT NULL AND e.confidence < :minImp");
            params.put("cutoff", Instant.now().minus(Duration.ofDays(policy.maxAgeDays())));
            params.put("minImp", policy.minConfidence());
        } else if (policy.maxAgeDays() != null) {
            jpql.append(" AND e.createdAt < :cutoff");
            params.put("cutoff", Instant.now().minus(Duration.ofDays(policy.maxAgeDays())));
        } else if (policy.minConfidence() != null) {
            jpql.append(" AND e.confidence IS NOT NULL AND e.confidence < :minImp");
            params.put("minImp", policy.minConfidence());
        }

        var query = em.createQuery(jpql.toString());
        params.forEach(query::setParameter);
        return query.executeUpdate();
    }

    private MemoryEntry toEntry(MemoryInput input) {
        MemoryEntry entry = new MemoryEntry();
        entry.memoryId = UUID.randomUUID().toString();
        entry.tenantId = input.tenantId();
        entry.entityId = input.subject().id();
        entry.domain = input.domain().name();
        entry.caseId = input.caseId();
        entry.text = input.text();
        entry.attributes = serializeAttributes(input.attributes());
        entry.createdAt = Instant.now();
        entry.confidence = input.confidence() != null ? input.confidence().value() : null;
        entry.pleasure = input.pleasure();
        entry.arousal = input.arousal();
        entry.dominance = input.dominance();
        entry.subjectType = input.subject().type();
        entry.principalId = input.principalId() != null ? input.principalId().value() : null;
        entry.sharedWith = serializeSharedWith(input.sharedWith());
        return entry;
    }

    private Memory toMemory(MemoryEntry e) {
        return new Memory(
                e.memoryId,
                Subject.of(e.subjectType != null ? e.subjectType : "unknown", e.entityId),
                new MemoryDomain(e.domain),
                e.tenantId,
                e.caseId,
                e.text,
                deserializeAttributes(e.attributes),
                e.createdAt,
                e.confidence != null ? Confidence.unknown(e.confidence) : null,
                e.pleasure, e.arousal, e.dominance,
                e.principalId != null ? PrincipalId.parse(e.principalId) : null,
                deserializeSharedWith(e.sharedWith));
    }

    private String serializeAttributes(Map<String, String> attrs) {
        try {
            return objectMapper.writeValueAsString(attrs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize attributes", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> deserializeAttributes(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize attributes: " + json, e);
        }
    }

    private String serializeSharedWith(Set<String> sharedWith) {
        if (sharedWith == null || sharedWith.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(sharedWith);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize sharedWith", e);
        }
    }

    private Set<String> deserializeSharedWith(String json) {
        if (json == null || json.isBlank()) return Set.of();
        try {
            @SuppressWarnings("unchecked")
            var list = objectMapper.readValue(json, java.util.List.class);
            return Set.copyOf(list);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize sharedWith: " + json, e);
        }
    }
}

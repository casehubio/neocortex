package io.casehub.neocortex.memory.cbr.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CaseTypeScope;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordFilterMatcher;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.CbrRecordSummary;
import io.casehub.neocortex.memory.cbr.CbrRecordValidator;
import io.casehub.neocortex.memory.cbr.CbrFilter;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import io.casehub.neocortex.memory.cbr.CbrSimilarityScorer;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.CbrPlanRecord;
import io.casehub.neocortex.memory.cbr.CbrPlanStep;
import io.casehub.neocortex.memory.cbr.RetrievalMode;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.SupersessionStatus;
import io.casehub.neocortex.memory.cbr.CbrGuidanceRecord;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(3)
@ApplicationScoped
public class JpaCbrRecordStore implements CbrRecordStore {

    private static final Logger                             LOG             = Logger.getLogger(JpaCbrRecordStore.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE        = new TypeReference<>() {};
    private static final TypeReference<List<CbrPlanStep>>   PLAN_TRACE_TYPE = new TypeReference<>() {};

    private final Map<String, CbrRecordSchema> schemas = new ConcurrentHashMap<>();

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void registerSchema(CbrRecordSchema schema) {
        schemas.put(schema.caseType(), schema);
    }

    @Override
    @Transactional
    public String store(CbrRecord cbrRecord, String caseType, String entityId, MemoryDomain domain,
                        String tenantId, String caseId, io.casehub.platform.api.path.Path scope) {
        CbrRecordSchema schema = schemas.get(caseType);
        if (schema != null) {
            CbrRecordValidator.validateStoreFeatures(cbrRecord.features(), schema);
        }

        CbrRecordEntity entity = new CbrRecordEntity();
        entity.id         = UUID.randomUUID().toString();
        entity.tenantId   = tenantId;
        entity.domain     = domain.name();
        entity.caseType   = caseType;
        entity.cbrType    = cbrRecord.recordType();
        entity.entityId   = entityId;
        entity.caseId     = caseId;
        entity.problem    = cbrRecord.problem();
        entity.solution   = cbrRecord.solution();
        entity.outcome    = cbrRecord.outcome();
        entity.confidence = cbrRecord.confidence() != null ? cbrRecord.confidence().value() : null;
        entity.features   = serializeJson(FeatureValue.toRawMap(cbrRecord.features()));
        entity.storedAt        = Instant.now();
        entity.scope           = scope.value();
        entity.trustScore      = cbrRecord.trustScore();
        entity.producerAgentId = cbrRecord.producerAgentId();

        if (cbrRecord instanceof CbrPlanRecord plan && !plan.cbrPlanStep().isEmpty()) {
            entity.planTraces = serializeJson(plan.cbrPlanStep());
        }

        em.persist(entity);
        return entity.id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(CbrQuery query, Class<C> caseClass) {
        if (query.retrievalMode() == RetrievalMode.SEMANTIC_ONLY) {
            return List.of();
        }
        if (query.retrievalMode() == RetrievalMode.HYBRID && query.problem() != null) {
            LOG.info("HYBRID mode degraded to FEATURE_ONLY — no EmbeddingModel available");
        }

        String queryCaseType = switch (query.caseTypeScope()) {
            case CaseTypeScope.Specific s -> s.caseType();
            case CaseTypeScope.AllInDomain a -> null;
        };

        CbrRecordSchema querySchema = queryCaseType != null ? schemas.get(queryCaseType) : null;

        if (queryCaseType != null && querySchema != null) {
            CbrRecordValidator.validateQueryFeatures(query.features(), querySchema);
        }
        if (!query.filters().isEmpty() && queryCaseType != null) {
            if (querySchema == null) {
                throw new IllegalStateException(
                        "Cannot apply structural filters: no schema registered for caseType '"
                        + queryCaseType + "'");
            }
            CbrRecordValidator.validateFilters(query.filters(), querySchema);
        }

        String scopeVal = query.scope().value();
        String jpql = "SELECT e FROM CbrRecordEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.supersededAt IS NULL"
                      + (queryCaseType != null ? " AND e.caseType = :ct" : "")
                      + " AND (e.scope = '' OR e.scope = :scopeVal OR :scopeVal LIKE CONCAT(e.scope, '/%'))"
                      + (query.notBefore() != null ? " AND e.storedAt >= :nb" : "");

        var jpaQuery = em.createQuery(jpql, CbrRecordEntity.class)
                         .setParameter("t", query.tenantId())
                         .setParameter("d", query.domain().name())
                         .setParameter("scopeVal", scopeVal);
        if (queryCaseType != null) {
            jpaQuery.setParameter("ct", queryCaseType);
        }
        if (query.notBefore() != null) {
            jpaQuery.setParameter("nb", query.notBefore());
        }

        List<CbrRecordEntity> entities   = jpaQuery.getResultList();
        List<CbrMatch<C>>     candidates = new ArrayList<>();

        for (CbrRecordEntity entity : entities) {
            CbrRecord reconstructed = reconstruct(entity);
            if (!caseClass.isInstance(reconstructed)) {continue;}

            CbrRecordSchema candidateSchema = queryCaseType != null
                                               ? querySchema
                                               : schemas.get(entity.caseType);

            if (!query.filters().isEmpty()) {
                if (candidateSchema == null) {continue;}
                if (!CbrRecordFilterMatcher.matchesFilters(reconstructed, query.filters(), candidateSchema)) {continue;}
            }

            CbrSimilarityScorer.SimilarityBreakdown breakdown = CbrSimilarityScorer.scoreDetailed(
                    query.features(), reconstructed.features(), query.weights(), candidateSchema, Map.of());

            double score = breakdown.score();
            if (score >= query.minSimilarity()) {
                io.casehub.platform.api.path.Path entityScope = entity.scope.isEmpty()
                                                                ? io.casehub.platform.api.path.Path.root()
                                                                : io.casehub.platform.api.path.Path.parse(entity.scope);
                candidates.add(new CbrMatch<>((C) reconstructed, entity.caseId, entity.caseType,
                                              score, false, breakdown.featureSimilarities(), entity.storedAt, entityScope, null));
            }
        }

        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<CbrMatch<C>> results = candidates.size() <= query.topK()
                                         ? candidates
                                         : candidates.subList(0, query.topK());
        return Collections.unmodifiableList(new ArrayList<>(results));
    }

    @Override
    @Transactional
    public Integer erase(EraseRequest request) {
        String jpql = "DELETE FROM CbrRecordEntity e WHERE e.entityId = :eid AND e.domain = :d AND e.tenantId = :t";
        if (request.caseId() != null) {
            jpql += " AND e.caseId = :cid";
        }
        var q = em.createQuery(jpql)
                  .setParameter("eid", request.entityId())
                  .setParameter("d", request.domain().name())
                  .setParameter("t", request.tenantId());
        if (request.caseId() != null) {
            q.setParameter("cid", request.caseId());
        }
        return q.executeUpdate();
    }

    @Override
    @Transactional
    public Integer eraseEntity(String entityId, String tenantId) {
        return em.createQuery("DELETE FROM CbrRecordEntity e WHERE e.entityId = :eid AND e.tenantId = :t")
                 .setParameter("eid", entityId)
                 .setParameter("t", tenantId)
                 .executeUpdate();
    }

    @Override
    @Transactional
    public Integer eraseByScope(io.casehub.platform.api.path.Path scope, String tenantId) {
        java.util.Objects.requireNonNull(scope, "scope required");
        java.util.Objects.requireNonNull(tenantId, "tenantId required");
        if (scope.segments().isEmpty()) {
            return em.createQuery("DELETE FROM CbrRecordEntity e WHERE e.tenantId = :t")
                     .setParameter("t", tenantId)
                     .executeUpdate();
        }
        return em.createQuery("DELETE FROM CbrRecordEntity e WHERE e.tenantId = :t AND (e.scope = :s OR e.scope LIKE :prefix)")
                 .setParameter("t", tenantId)
                 .setParameter("s", scope.value())
                 .setParameter("prefix", scope.value() + "/%")
                 .executeUpdate();
    }


    @Override
    @Transactional
    public void recordOutcome(String caseId, String tenantId, CbrOutcome outcome) {
        var results = em.createQuery(
                                "SELECT c FROM CbrRecordEntity c WHERE c.caseId = :caseId AND c.tenantId = :tenantId",
                                CbrRecordEntity.class)
                        .setParameter("caseId", caseId)
                        .setParameter("tenantId", tenantId)
                        .getResultList();
        if (results.isEmpty()) {return;}
        CbrRecordEntity entity = results.getFirst();
        if (entity.lastOutcomeAt != null
            && !outcome.observedAt().isAfter(entity.lastOutcomeAt)) {
            return;
        }
        CbrRecordSchema schema = schemas.get(entity.caseType);
        double lr = (schema != null && schema.learningRate() != null)
                    ? schema.learningRate() : CbrOutcome.DEFAULT_LEARNING_RATE;
        io.casehub.neocortex.cognitive.Confidence newConf = CbrOutcome.adjustConfidence(
                entity.confidence != null ? io.casehub.neocortex.cognitive.Confidence.unknown(entity.confidence) : null,
                outcome.successRate(), lr);
        entity.outcome       = outcome.result().name();
        entity.confidence    = newConf.value();
        entity.outcomeDetail = outcome.detail();
        entity.lastOutcomeAt = outcome.observedAt();
        em.merge(entity);
    }

    @Override
    @Transactional
    public Integer purge(CbrRetentionPolicy policy) {
        int    deleted        = 0;
        String caseTypeFilter = policy.caseType() != null ? " AND e.caseType = :ct" : "";
        if (policy.maxAgeDays() != null) {
            Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(policy.maxAgeDays()));
            String  jpql   = "DELETE FROM CbrRecordEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.storedAt < :cutoff" + caseTypeFilter;
            var     q      = em.createQuery(jpql).setParameter("t", policy.tenantId()).setParameter("d", policy.domain().name()).setParameter("cutoff", cutoff);
            if (policy.caseType() != null) {q.setParameter("ct", policy.caseType());}
            deleted += q.executeUpdate();
        }
        if (policy.maxCasesPerType() != null) {
            String typeJpql  = "SELECT DISTINCT e.caseType FROM CbrRecordEntity e WHERE e.tenantId = :t AND e.domain = :d" + caseTypeFilter;
            var    typeQuery = em.createQuery(typeJpql, String.class).setParameter("t", policy.tenantId()).setParameter("d", policy.domain().name());
            if (policy.caseType() != null) {typeQuery.setParameter("ct", policy.caseType());}
            for (String ct : typeQuery.getResultList()) {
                var ids = em.createQuery("SELECT e.id FROM CbrRecordEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.caseType = :ct ORDER BY e.storedAt DESC", String.class)
                            .setParameter("t", policy.tenantId()).setParameter("d", policy.domain().name()).setParameter("ct", ct).getResultList();
                if (ids.size() > policy.maxCasesPerType()) {
                    List<String> toDelete = ids.subList(policy.maxCasesPerType(), ids.size());
                    deleted += em.createQuery("DELETE FROM CbrRecordEntity e WHERE e.id IN :ids").setParameter("ids", toDelete).executeUpdate();
                }
            }
        }
        if (policy.minTrustScore() != null) {
            String jpql = "DELETE FROM CbrRecordEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.trustScore IS NOT NULL AND e.trustScore < :minTrust" + caseTypeFilter;
            var    q    = em.createQuery(jpql).setParameter("t", policy.tenantId()).setParameter("d", policy.domain().name()).setParameter("minTrust", policy.minTrustScore());
            if (policy.caseType() != null) {q.setParameter("ct", policy.caseType());}
            deleted += q.executeUpdate();
        }
        return deleted;}

    @Override
    @Transactional
    public java.util.Set<String> discoverTenants(io.casehub.neocortex.memory.MemoryDomain domain) {
        return new java.util.LinkedHashSet<>(
                em.createQuery("SELECT DISTINCT e.tenantId FROM CbrRecordEntity e WHERE e.domain = :d", String.class)
                  .setParameter("d", domain.name())
                  .getResultList());
    }

    @Override
    public io.casehub.neocortex.memory.cbr.CbrScanResult scan(io.casehub.neocortex.memory.cbr.CbrScanRequest request) {
        String jpql = "SELECT e FROM CbrRecordEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.caseType = :ct"
                      + (request.cursor() != null ? " AND e.caseId > :afterId" : "")
                      + " ORDER BY e.caseId";
        var q = em.createQuery(jpql, CbrRecordEntity.class)
                  .setParameter("t", request.tenantId())
                  .setParameter("d", request.domain().name())
                  .setParameter("ct", request.caseType())
                  .setMaxResults(request.limit());
        if (request.cursor() != null) {q.setParameter("afterId", request.cursor());}
        var items = q.getResultList().stream()
                     .map(e -> new CbrRecordSummary(
                             e.caseId, e.entityId, e.caseType, e.producerAgentId, e.trustScore, e.storedAt))
                     .toList();
        String nextCursor = items.size() >= request.limit() ? items.get(items.size() - 1).caseId() : null;
        return new io.casehub.neocortex.memory.cbr.CbrScanResult(items, nextCursor);
    }


    @Override
    @Transactional
    public boolean supersede(String caseId, String tenantId, String supersedingCaseId, String reason) {
        java.util.Objects.requireNonNull(caseId, "caseId required");
        java.util.Objects.requireNonNull(tenantId, "tenantId required");
        var results = em.createQuery("SELECT e FROM CbrRecordEntity e WHERE e.caseId = :cid AND e.tenantId = :t", CbrRecordEntity.class)
                        .setParameter("cid", caseId).setParameter("t", tenantId).getResultList();
        if (results.isEmpty()) return false;
        CbrRecordEntity entity = results.getFirst();
        if (entity.supersededAt != null) {
            return false;
        }
        entity.supersededAt = Instant.now();
        entity.supersedingCaseId = supersedingCaseId;
        entity.supersessionReason = reason;
        entity.reinstatedAt = null;
        return true;
    }

    @Override
    @Transactional
    public boolean reinstate(String caseId, String tenantId) {
        java.util.Objects.requireNonNull(caseId, "caseId required");
        java.util.Objects.requireNonNull(tenantId, "tenantId required");
        var results = em.createQuery("SELECT e FROM CbrRecordEntity e WHERE e.caseId = :cid AND e.tenantId = :t", CbrRecordEntity.class)
                        .setParameter("cid", caseId).setParameter("t", tenantId).getResultList();
        if (results.isEmpty()) return false;
        CbrRecordEntity entity = results.getFirst();
        if (entity.supersededAt == null) {
            return false;
        }
        entity.reinstatedAt = Instant.now();
        entity.supersededAt = null;
        entity.supersedingCaseId = null;
        entity.supersessionReason = null;
        return true;
    }

    @Override
    public List<String> findCaseIds(String tenantId, MemoryDomain domain,
                                     String caseType, Map<String, CbrFilter> filters) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(domain, "domain required");
        Objects.requireNonNull(caseType, "caseType required");
        Objects.requireNonNull(filters, "filters required");

        CbrRecordSchema schema = schemas.get(caseType);
        if (!filters.isEmpty()) {
            if (schema == null) {
                throw new IllegalStateException(
                        "Cannot apply filters: no schema registered for caseType '" + caseType + "'");
            }
            CbrRecordValidator.validateFilters(filters, schema);
        }

        var entities = em.createQuery(
                        "SELECT e FROM CbrRecordEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.caseType = :ct AND e.supersededAt IS NULL",
                        CbrRecordEntity.class)
                .setParameter("t", tenantId).setParameter("d", domain.name()).setParameter("ct", caseType)
                .getResultList();

        List<String> result = new ArrayList<>();
        for (CbrRecordEntity entity : entities) {
            if (filters.isEmpty() || CbrRecordFilterMatcher.matchesFilters(reconstruct(entity), filters, schema)) {
                result.add(entity.caseId);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public int supersedeMatching(String tenantId, MemoryDomain domain, String caseType,
                                  Map<String, CbrFilter> filters, String reason) {
        List<String> ids = findCaseIds(tenantId, domain, caseType, filters);
        if (ids.isEmpty()) return 0;
        int count = 0;
        for (String id : ids) {
            if (supersede(id, tenantId, null, reason)) {
                count++;
            }
        }
        return count;
    }

    @Override
    @Transactional
    public int supersedeAll(Collection<String> caseIds, String tenantId, String reason) {
        Objects.requireNonNull(caseIds, "caseIds required");
        Objects.requireNonNull(tenantId, "tenantId required");
        int count = 0;
        for (String caseId : caseIds) {
            if (supersede(caseId, tenantId, null, reason)) {
                count++;
            }
        }
        return count;
    }

    @Override
    @Transactional
    public int reinstateMatching(String tenantId, MemoryDomain domain, String caseType,
                                  Map<String, CbrFilter> filters) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(domain, "domain required");
        Objects.requireNonNull(caseType, "caseType required");
        Objects.requireNonNull(filters, "filters required");

        CbrRecordSchema schema = schemas.get(caseType);
        if (!filters.isEmpty()) {
            if (schema == null) {
                throw new IllegalStateException(
                        "Cannot apply filters: no schema registered for caseType '" + caseType + "'");
            }
            CbrRecordValidator.validateFilters(filters, schema);
        }

        var entities = em.createQuery(
                        "SELECT e FROM CbrRecordEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.caseType = :ct AND e.supersededAt IS NOT NULL",
                        CbrRecordEntity.class)
                .setParameter("t", tenantId).setParameter("d", domain.name()).setParameter("ct", caseType)
                .getResultList();

        int count = 0;
        for (CbrRecordEntity entity : entities) {
            if (filters.isEmpty() || CbrRecordFilterMatcher.matchesFilters(reconstruct(entity), filters, schema)) {
                if (reinstate(entity.caseId, tenantId)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    @Transactional
    public int reinstateAll(Collection<String> caseIds, String tenantId) {
        Objects.requireNonNull(caseIds, "caseIds required");
        Objects.requireNonNull(tenantId, "tenantId required");
        int count = 0;
        for (String caseId : caseIds) {
            if (reinstate(caseId, tenantId)) {
                count++;
            }
        }
        return count;
    }

    private CbrRecord reconstruct(CbrRecordEntity entity) {
        Map<String, FeatureValue> features = deserializeFeatures(entity.features);
        io.casehub.neocortex.cognitive.Confidence confidence = entity.confidence != null
                ? io.casehub.neocortex.cognitive.Confidence.unknown(entity.confidence) : null;
        return switch (entity.cbrType) {
            case "plan" -> new CbrPlanRecord(
                    entity.problem, entity.solution, entity.outcome, confidence,
                    features, deserializePlanTraces(entity.planTraces), entity.trustScore, entity.producerAgentId);
            case "feature-vector" -> new CbrFeatureRecord(
                    entity.problem, entity.solution, entity.outcome, confidence, features, entity.trustScore, entity.producerAgentId);
            case "textual" -> new CbrGuidanceRecord(
                    entity.problem, entity.solution, entity.outcome, confidence, entity.trustScore, entity.producerAgentId);
            default -> new CbrFeatureRecord(
                    entity.problem, entity.solution, entity.outcome, confidence, features, entity.trustScore, entity.producerAgentId);
        };}

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON", e);
        }
    }

    private Map<String, FeatureValue> deserializeFeatures(String json) {
        if (json == null || json.isBlank()) {return Map.of();}
        try {
            Map<String, Object> raw = objectMapper.readValue(json, MAP_TYPE);
            return FeatureValue.toFeatureMap(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize features JSON", e);
        }
    }

    private List<CbrPlanStep> deserializePlanTraces(String json) {
        if (json == null || json.isBlank()) {return List.of();}
        try {
            return objectMapper.readValue(json, PLAN_TRACE_TYPE);
        } catch (JsonProcessingException e) {
            LOG.warnf("Failed to deserialize plan traces: %s", e.getMessage());
            return List.of();
        }
    }

    @Override
    public SupersessionStatus getSupersessionStatus(String caseId, String tenantId) {
        java.util.Objects.requireNonNull(caseId, "caseId required");
        java.util.Objects.requireNonNull(tenantId, "tenantId required");
        var results = em.createQuery("SELECT e FROM CbrRecordEntity e WHERE e.caseId = :cid AND e.tenantId = :t", CbrRecordEntity.class)
                        .setParameter("cid", caseId).setParameter("t", tenantId).getResultList();
        if (results.isEmpty()) return SupersessionStatus.NOT_SUPERSEDED;
        CbrRecordEntity entity = results.getFirst();
        if (entity.supersededAt != null) {
            return new SupersessionStatus(caseId, true, entity.supersededAt,
                    entity.supersedingCaseId, entity.supersessionReason, entity.reinstatedAt);
        }
        return new SupersessionStatus(caseId, false, null, null, null, entity.reinstatedAt);
    }

    @Override
    public List<SupersessionStatus> findSupersededCases(String tenantId, MemoryDomain domain) {
        java.util.Objects.requireNonNull(tenantId, "tenantId required");
        java.util.Objects.requireNonNull(domain, "domain required");
        var results = em.createQuery(
                "SELECT e FROM CbrRecordEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.supersededAt IS NOT NULL",
                CbrRecordEntity.class)
                .setParameter("t", tenantId).setParameter("d", domain.name()).getResultList();
        return results.stream().map(e -> new SupersessionStatus(e.caseId, true, e.supersededAt,
                e.supersedingCaseId, e.supersessionReason, e.reinstatedAt)).toList();
    }

}

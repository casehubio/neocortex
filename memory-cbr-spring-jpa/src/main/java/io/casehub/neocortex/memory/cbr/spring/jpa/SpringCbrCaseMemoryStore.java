package io.casehub.neocortex.memory.cbr.spring.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CaseTypeScope;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseFilterMatcher;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrFeatureSchema;
import io.casehub.neocortex.memory.cbr.CbrFeatureValidator;
import io.casehub.neocortex.memory.cbr.CbrFilter;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;
import io.casehub.neocortex.memory.cbr.CbrScanResult;
import io.casehub.neocortex.memory.cbr.CbrCaseSummary;
import io.casehub.neocortex.memory.cbr.CbrSimilarityScorer;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ResolvedCase;
import io.casehub.neocortex.memory.cbr.ResolutionGuide;
import io.casehub.neocortex.memory.cbr.ResolutionStep;
import io.casehub.neocortex.memory.cbr.RetrievalMode;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.SupersessionStatus;
import io.casehub.neocortex.memory.cbr.jpa.CbrCaseEntity;
import io.casehub.platform.api.path.Path;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SpringCbrCaseMemoryStore implements CbrCaseMemoryStore {

    private static final Logger LOG = Logger.getLogger(SpringCbrCaseMemoryStore.class.getName());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ResolutionStep>> PLAN_TRACE_TYPE = new TypeReference<>() {};

    private final Map<String, CbrFeatureSchema> schemas = new ConcurrentHashMap<>();
    private final CbrCaseEntityRepository repo;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager em;

    public SpringCbrCaseMemoryStore(CbrCaseEntityRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerSchema(CbrFeatureSchema schema) {
        schemas.put(schema.caseType(), schema);
    }

    @Override
    @Transactional
    public String store(CbrCase cbrCase, String caseType, String entityId, MemoryDomain domain,
                        String tenantId, String caseId, Path scope) {
        CbrFeatureSchema schema = schemas.get(caseType);
        if (schema != null) {
            CbrFeatureValidator.validateStoreFeatures(cbrCase.features(), schema);
        }

        CbrCaseEntity entity = new CbrCaseEntity();
        entity.id         = UUID.randomUUID().toString();
        entity.tenantId   = tenantId;
        entity.domain     = domain.name();
        entity.caseType   = caseType;
        entity.cbrType    = cbrCase.cbrType();
        entity.entityId   = entityId;
        entity.caseId     = caseId;
        entity.problem    = cbrCase.problem();
        entity.solution   = cbrCase.solution();
        entity.outcome    = cbrCase.outcome();
        entity.confidence = cbrCase.confidence() != null ? cbrCase.confidence().value() : null;
        entity.features   = serializeJson(FeatureValue.toRawMap(cbrCase.features()));
        entity.storedAt        = Instant.now();
        entity.scope           = scope.value();
        entity.trustScore      = cbrCase.trustScore();
        entity.producerAgentId = cbrCase.producerAgentId();

        if (cbrCase instanceof ResolvedCase plan && !plan.resolutionStep().isEmpty()) {
            entity.planTraces = serializeJson(plan.resolutionStep());
        }

        repo.save(entity);
        return entity.id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(CbrQuery query, Class<C> caseClass) {
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

        CbrFeatureSchema querySchema = queryCaseType != null ? schemas.get(queryCaseType) : null;

        if (queryCaseType != null && querySchema != null) {
            CbrFeatureValidator.validateQueryFeatures(query.features(), querySchema);
        }
        if (!query.filters().isEmpty() && queryCaseType != null) {
            if (querySchema == null) {
                throw new IllegalStateException(
                        "Cannot apply structural filters: no schema registered for caseType '"
                        + queryCaseType + "'");
            }
            CbrFeatureValidator.validateFilters(query.filters(), querySchema);
        }

        String scopeVal = query.scope().value();
        String jpql = "SELECT e FROM CbrCaseEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.supersededAt IS NULL"
                      + (queryCaseType != null ? " AND e.caseType = :ct" : "")
                      + " AND (e.scope = '' OR e.scope = :scopeVal OR :scopeVal LIKE CONCAT(e.scope, '/%'))"
                      + (query.notBefore() != null ? " AND e.storedAt >= :nb" : "");

        var jpaQuery = em.createQuery(jpql, CbrCaseEntity.class)
                         .setParameter("t", query.tenantId())
                         .setParameter("d", query.domain().name())
                         .setParameter("scopeVal", scopeVal);
        if (queryCaseType != null) {
            jpaQuery.setParameter("ct", queryCaseType);
        }
        if (query.notBefore() != null) {
            jpaQuery.setParameter("nb", query.notBefore());
        }

        List<CbrCaseEntity> entities = jpaQuery.getResultList();
        List<ScoredCbrCase<C>> candidates = new ArrayList<>();

        for (CbrCaseEntity entity : entities) {
            CbrCase reconstructed = reconstruct(entity);
            if (!caseClass.isInstance(reconstructed)) { continue; }

            CbrFeatureSchema candidateSchema = queryCaseType != null
                                               ? querySchema
                                               : schemas.get(entity.caseType);

            if (!query.filters().isEmpty()) {
                if (candidateSchema == null) { continue; }
                if (!CbrCaseFilterMatcher.matchesFilters(reconstructed, query.filters(), candidateSchema)) { continue; }
            }

            CbrSimilarityScorer.SimilarityBreakdown breakdown = CbrSimilarityScorer.scoreDetailed(
                    query.features(), reconstructed.features(), query.weights(), candidateSchema, Map.of());

            double score = breakdown.score();
            if (score >= query.minSimilarity()) {
                Path entityScope = entity.scope.isEmpty()
                                   ? Path.root()
                                   : Path.parse(entity.scope);
                candidates.add(new ScoredCbrCase<>((C) reconstructed, entity.caseId, entity.caseType,
                                                   score, false, breakdown.featureSimilarities(), entity.storedAt, entityScope, null));
            }
        }

        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<ScoredCbrCase<C>> results = candidates.size() <= query.topK()
                                         ? candidates
                                         : candidates.subList(0, query.topK());
        return Collections.unmodifiableList(new ArrayList<>(results));
    }

    @Override
    @Transactional
    public Integer erase(EraseRequest request) {
        if (request.caseId() != null) {
            return repo.eraseByEntityDomainTenantCase(
                    request.entityId(), request.domain().name(), request.tenantId(), request.caseId());
        }
        return repo.eraseByEntityDomainTenant(
                request.entityId(), request.domain().name(), request.tenantId());
    }

    @Override
    @Transactional
    public Integer eraseEntity(String entityId, String tenantId) {
        return repo.eraseEntity(entityId, tenantId);
    }

    @Override
    @Transactional
    public Integer eraseByScope(Path scope, String tenantId) {
        Objects.requireNonNull(scope, "scope required");
        Objects.requireNonNull(tenantId, "tenantId required");
        if (scope.segments().isEmpty()) {
            return em.createQuery("DELETE FROM CbrCaseEntity e WHERE e.tenantId = :t")
                     .setParameter("t", tenantId)
                     .executeUpdate();
        }
        return em.createQuery("DELETE FROM CbrCaseEntity e WHERE e.tenantId = :t AND (e.scope = :s OR e.scope LIKE :prefix)")
                 .setParameter("t", tenantId)
                 .setParameter("s", scope.value())
                 .setParameter("prefix", scope.value() + "/%")
                 .executeUpdate();
    }

    @Override
    @Transactional
    public void recordOutcome(String caseId, String tenantId, CbrOutcome outcome) {
        var results = repo.findByCaseIdAndTenantId(caseId, tenantId);
        if (results.isEmpty()) { return; }
        CbrCaseEntity entity = results.getFirst();
        if (entity.lastOutcomeAt != null && !outcome.observedAt().isAfter(entity.lastOutcomeAt)) {
            return;
        }
        CbrFeatureSchema schema = schemas.get(entity.caseType);
        double lr = (schema != null && schema.learningRate() != null)
                    ? schema.learningRate() : CbrOutcome.DEFAULT_LEARNING_RATE;
        io.casehub.neocortex.cognitive.Confidence newConf = CbrOutcome.adjustConfidence(
                entity.confidence != null ? io.casehub.neocortex.cognitive.Confidence.unknown(entity.confidence) : null,
                outcome.successRate(), lr);
        entity.outcome       = outcome.result().name();
        entity.confidence    = newConf.value();
        entity.outcomeDetail = outcome.detail();
        entity.lastOutcomeAt = outcome.observedAt();
        repo.save(entity);
    }

    @Override
    @Transactional
    public Integer purge(CbrRetentionPolicy policy) {
        int deleted = 0;
        String caseTypeFilter = policy.caseType() != null ? " AND e.caseType = :ct" : "";
        if (policy.maxAgeDays() != null) {
            Instant cutoff = Instant.now().minus(Duration.ofDays(policy.maxAgeDays()));
            String jpql = "DELETE FROM CbrCaseEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.storedAt < :cutoff" + caseTypeFilter;
            var q = em.createQuery(jpql).setParameter("t", policy.tenantId()).setParameter("d", policy.domain().name()).setParameter("cutoff", cutoff);
            if (policy.caseType() != null) { q.setParameter("ct", policy.caseType()); }
            deleted += q.executeUpdate();
        }
        if (policy.maxCasesPerType() != null) {
            String typeJpql = "SELECT DISTINCT e.caseType FROM CbrCaseEntity e WHERE e.tenantId = :t AND e.domain = :d" + caseTypeFilter;
            var typeQuery = em.createQuery(typeJpql, String.class).setParameter("t", policy.tenantId()).setParameter("d", policy.domain().name());
            if (policy.caseType() != null) { typeQuery.setParameter("ct", policy.caseType()); }
            for (String ct : typeQuery.getResultList()) {
                var ids = em.createQuery("SELECT e.id FROM CbrCaseEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.caseType = :ct ORDER BY e.storedAt DESC", String.class)
                            .setParameter("t", policy.tenantId()).setParameter("d", policy.domain().name()).setParameter("ct", ct).getResultList();
                if (ids.size() > policy.maxCasesPerType()) {
                    List<String> toDelete = ids.subList(policy.maxCasesPerType(), ids.size());
                    deleted += em.createQuery("DELETE FROM CbrCaseEntity e WHERE e.id IN :ids").setParameter("ids", toDelete).executeUpdate();
                }
            }
        }
        if (policy.minTrustScore() != null) {
            String jpql = "DELETE FROM CbrCaseEntity e WHERE e.tenantId = :t AND e.domain = :d AND e.trustScore IS NOT NULL AND e.trustScore < :minTrust" + caseTypeFilter;
            var q = em.createQuery(jpql).setParameter("t", policy.tenantId()).setParameter("d", policy.domain().name()).setParameter("minTrust", policy.minTrustScore());
            if (policy.caseType() != null) { q.setParameter("ct", policy.caseType()); }
            deleted += q.executeUpdate();
        }
        return deleted;
    }

    @Override
    public Set<String> discoverTenants(MemoryDomain domain) {
        return new LinkedHashSet<>(repo.discoverTenants(domain.name()));
    }

    @Override
    public CbrScanResult scan(CbrScanRequest request) {
        List<CbrCaseEntity> entities;
        if (request.cursor() != null) {
            entities = repo.findForScanAfterCursor(
                    request.tenantId(), request.domain().name(), request.caseType(), request.cursor());
        } else {
            entities = repo.findForScan(
                    request.tenantId(), request.domain().name(), request.caseType());
        }
        if (entities.size() > request.limit()) {
            entities = entities.subList(0, request.limit());
        }
        var items = entities.stream()
                .map(e -> new CbrCaseSummary(e.caseId, e.entityId, e.caseType, e.producerAgentId, e.trustScore, e.storedAt))
                .toList();
        String nextCursor = items.size() >= request.limit() ? items.get(items.size() - 1).caseId() : null;
        return new CbrScanResult(items, nextCursor);
    }

    @Override
    @Transactional
    public boolean supersede(String caseId, String tenantId, String supersedingCaseId, String reason) {
        Objects.requireNonNull(caseId, "caseId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        var results = repo.findByCaseIdAndTenantId(caseId, tenantId);
        if (results.isEmpty()) return false;
        CbrCaseEntity entity = results.getFirst();
        if (entity.supersededAt != null) { return false; }
        entity.supersededAt = Instant.now();
        entity.supersedingCaseId = supersedingCaseId;
        entity.supersessionReason = reason;
        entity.reinstatedAt = null;
        repo.save(entity);
        return true;
    }

    @Override
    @Transactional
    public boolean reinstate(String caseId, String tenantId) {
        Objects.requireNonNull(caseId, "caseId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        var results = repo.findByCaseIdAndTenantId(caseId, tenantId);
        if (results.isEmpty()) return false;
        CbrCaseEntity entity = results.getFirst();
        if (entity.supersededAt == null) { return false; }
        entity.reinstatedAt = Instant.now();
        entity.supersededAt = null;
        entity.supersedingCaseId = null;
        entity.supersessionReason = null;
        repo.save(entity);
        return true;
    }

    @Override
    public SupersessionStatus getSupersessionStatus(String caseId, String tenantId) {
        Objects.requireNonNull(caseId, "caseId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        var results = repo.findByCaseIdAndTenantId(caseId, tenantId);
        if (results.isEmpty()) return SupersessionStatus.NOT_SUPERSEDED;
        CbrCaseEntity entity = results.getFirst();
        if (entity.supersededAt != null) {
            return new SupersessionStatus(caseId, true, entity.supersededAt,
                    entity.supersedingCaseId, entity.supersessionReason, entity.reinstatedAt);
        }
        return new SupersessionStatus(caseId, false, null, null, null, entity.reinstatedAt);
    }

    @Override
    public List<SupersessionStatus> findSupersededCases(String tenantId, MemoryDomain domain) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(domain, "domain required");
        var results = repo.findSuperseded(tenantId, domain.name());
        return results.stream().map(e -> new SupersessionStatus(e.caseId, true, e.supersededAt,
                e.supersedingCaseId, e.supersessionReason, e.reinstatedAt)).toList();
    }

    @Override
    public List<String> findCaseIds(String tenantId, MemoryDomain domain,
                                     String caseType, Map<String, CbrFilter> filters) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(domain, "domain required");
        Objects.requireNonNull(caseType, "caseType required");
        Objects.requireNonNull(filters, "filters required");

        CbrFeatureSchema schema = schemas.get(caseType);
        if (!filters.isEmpty()) {
            if (schema == null) {
                throw new IllegalStateException(
                        "Cannot apply filters: no schema registered for caseType '" + caseType + "'");
            }
            CbrFeatureValidator.validateFilters(filters, schema);
        }

        var entities = repo.findActiveCases(tenantId, domain.name(), caseType);
        List<String> result = new ArrayList<>();
        for (CbrCaseEntity entity : entities) {
            if (filters.isEmpty() || CbrCaseFilterMatcher.matchesFilters(reconstruct(entity), filters, schema)) {
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
            if (supersede(id, tenantId, null, reason)) { count++; }
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
            if (supersede(caseId, tenantId, null, reason)) { count++; }
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

        CbrFeatureSchema schema = schemas.get(caseType);
        if (!filters.isEmpty()) {
            if (schema == null) {
                throw new IllegalStateException(
                        "Cannot apply filters: no schema registered for caseType '" + caseType + "'");
            }
            CbrFeatureValidator.validateFilters(filters, schema);
        }

        var entities = repo.findSupersededByType(tenantId, domain.name(), caseType);
        int count = 0;
        for (CbrCaseEntity entity : entities) {
            if (filters.isEmpty() || CbrCaseFilterMatcher.matchesFilters(reconstruct(entity), filters, schema)) {
                if (reinstate(entity.caseId, tenantId)) { count++; }
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
            if (reinstate(caseId, tenantId)) { count++; }
        }
        return count;
    }

    private CbrCase reconstruct(CbrCaseEntity entity) {
        Map<String, FeatureValue> features = deserializeFeatures(entity.features);
        io.casehub.neocortex.cognitive.Confidence confidence = entity.confidence != null
                ? io.casehub.neocortex.cognitive.Confidence.unknown(entity.confidence) : null;
        return switch (entity.cbrType) {
            case "plan" -> new ResolvedCase(
                    entity.problem, entity.solution, entity.outcome, confidence,
                    features, deserializePlanTraces(entity.planTraces), entity.trustScore, entity.producerAgentId);
            case "feature-vector" -> new FeatureVectorCbrCase(
                    entity.problem, entity.solution, entity.outcome, confidence, features, entity.trustScore, entity.producerAgentId);
            case "textual" -> new ResolutionGuide(
                    entity.problem, entity.solution, entity.outcome, confidence, entity.trustScore, entity.producerAgentId);
            default -> new FeatureVectorCbrCase(
                    entity.problem, entity.solution, entity.outcome, confidence, features, entity.trustScore, entity.producerAgentId);
        };
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON", e);
        }
    }

    private Map<String, FeatureValue> deserializeFeatures(String json) {
        if (json == null || json.isBlank()) { return Map.of(); }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, MAP_TYPE);
            return FeatureValue.toFeatureMap(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize features JSON", e);
        }
    }

    private List<ResolutionStep> deserializePlanTraces(String json) {
        if (json == null || json.isBlank()) { return List.of(); }
        try {
            return objectMapper.readValue(json, PLAN_TRACE_TYPE);
        } catch (JsonProcessingException e) {
            LOG.warning("Failed to deserialize plan traces: " + e.getMessage());
            return List.of();
        }
    }
}

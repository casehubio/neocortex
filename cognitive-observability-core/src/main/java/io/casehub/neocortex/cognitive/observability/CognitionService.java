package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.index.AffectTrajectory;
import io.casehub.neocortex.cognitive.index.AffectTrajectoryAnalyzer;
import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognitive.index.CognitiveProfileQuery;
import io.casehub.neocortex.cognitive.index.DomainActivation;
import io.casehub.neocortex.cognitive.index.DomainActivationQuery;
import io.casehub.neocortex.cognitive.index.DomainActivationResult;
import io.casehub.neocortex.cognitive.index.EntityKnowledge;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.mindmap.AttentionBriefing;
import io.casehub.neocortex.mindmap.intelligence.consolidation.CognitiveAttentionAccumulator;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.intelligence.ActivityQueryService;
import io.casehub.neocortex.mindmap.intelligence.ActivitySummary;
import io.casehub.neocortex.mindmap.runtime.MindMapAnalyzer;
import io.casehub.platform.api.identity.PrincipalId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class CognitionService implements CognitionApi {

    private final MindMapStore         store;
    private final CognitiveProfile     cognitiveProfile;
    private final SnapshotStore        snapshotStore;
    private final CaseMemoryStore      memoryStore;
    private final DomainActivation     domainActivation;
    private final ActivityQueryService activityQueryService;
    private final CognitiveAttentionAccumulator attentionAccumulator;


    public CognitionService(MindMapStore store,
                            CognitiveProfile cognitiveProfile,
                            SnapshotStore snapshotStore,
                            CaseMemoryStore memoryStore,
                            DomainActivation domainActivation,
                            ActivityQueryService activityQueryService,
                            CognitiveAttentionAccumulator attentionAccumulator) {
        this.store                = store;
        this.cognitiveProfile     = cognitiveProfile;
        this.snapshotStore        = snapshotStore;
        this.memoryStore          = memoryStore;
        this.domainActivation     = domainActivation;
        this.activityQueryService = activityQueryService;
        this.attentionAccumulator = attentionAccumulator;
    }

    @Override
    public CognitionInspectResult inspect(String tenancyId, String subgraphId) {
        return CognitionInspectService.inspect(store, tenancyId, subgraphId);
    }

    @Override
    public EntityKnowledge entity(String tenancyId, String entityName, String nodeId,
                                  String subgraphId, Boolean includeMemories, Integer memoryLimit) {
        if (cognitiveProfile == null) {
            return null;
        }
        CognitiveProfileQuery query;
        if (nodeId != null) {
            query = CognitiveProfileQuery.byId(nodeId, tenancyId);
        } else if (subgraphId != null) {
            query = CognitiveProfileQuery.byName(entityName, subgraphId, tenancyId);
        } else {
            query = CognitiveProfileQuery.byName(entityName, tenancyId);
        }
        boolean doInclude = includeMemories == null || includeMemories;
        int     limit     = memoryLimit != null ? memoryLimit : 10;
        if (!doInclude) {
            query = query.withMemoryLimit(0);
        } else {
            query = query.withMemoryLimit(limit);
        }
        Optional<EntityKnowledge> result = cognitiveProfile.resolve(query);
        return result.orElse(null);
    }

    @Override
    public GraphHealthReport health(String tenancyId, String subgraphId,
                                    Integer staleThresholdDays, Double lowConfidenceThreshold) {
        int    staleDays     = staleThresholdDays != null ? staleThresholdDays : 30;
        double confThreshold = lowConfidenceThreshold != null ? lowConfidenceThreshold : 0.3;
        return CognitionHealthService.health(store, tenancyId, subgraphId, staleDays, confThreshold);
    }

    @Override
    public GraphDiffResult diff(String tenancyId, String subgraphId,
                                String from, String to, String source) {
        if (snapshotStore == null) {
            return null;
        }
        Instant fromInstant = from != null ? Instant.parse(from) : null;
        Instant toInstant   = to != null ? Instant.parse(to) : null;
        return CognitionDiffService.diff(snapshotStore, tenancyId, subgraphId,
                                         fromInstant, toInstant, source);
    }

    @Override
    public EntityTrace trace(String tenancyId, String entityName, String nodeId,
                             String subgraphId, String from, String to) {
        if (snapshotStore == null) {
            return null;
        }
        String resolvedNodeId = nodeId;
        if (resolvedNodeId == null && entityName != null) {
            var node = subgraphId != null
                       ? store.resolveNode(entityName, subgraphId, tenancyId)
                       : store.resolveNode(entityName, null, tenancyId);
            if (node != null) {
                resolvedNodeId = node.id();
            }
        }
        if (resolvedNodeId == null) {
            return new EntityTrace(nodeId, entityName, java.util.List.of());
        }
        Instant fromInstant = from != null ? Instant.parse(from) : null;
        Instant toInstant   = to != null ? Instant.parse(to) : null;
        return CognitionTraceService.trace(snapshotStore, tenancyId,
                                           resolvedNodeId, fromInstant, toInstant);
    }

    @Override
    public GraphTraversalResult graph(String tenancyId, String nodeId, Integer maxDepth,
                                      Double minConfidence, String subgraphId) {
        int depth = maxDepth != null ? maxDepth : 2;
        return GraphTraversalService.traverse(store, tenancyId, nodeId, depth, minConfidence);
    }

    @Override
    public AffectTrajectory affect(String tenancyId, String entityName, String nodeId, String subgraphId) {
        if (memoryStore == null) {
            return null;
        }
        String resolvedNodeId = nodeId;
        if (resolvedNodeId == null && entityName != null) {
            var node = subgraphId != null
                       ? store.resolveNode(entityName, subgraphId, tenancyId)
                       : store.resolveNode(entityName, null, tenancyId);
            if (node != null) {
                resolvedNodeId = node.id();
            }
        }
        MemoryQuery query;
        if (resolvedNodeId != null) {
            query = MemoryQuery.forEntity(resolvedNodeId, new MemoryDomain("affect"), tenancyId).withLimit(1000);
        } else {
            query = MemoryQuery.forSubject(Subject.of("agent", "global"), new MemoryDomain("affect"), tenancyId).withLimit(1000);
        }
        List<Memory> affectMemories = memoryStore.query(query);
        if (affectMemories.isEmpty()) {
            return null;
        }
        return AffectTrajectoryAnalyzer.analyze(affectMemories);
    }

    @Override
    public AttentionBriefing attention(String tenancyId, String principalId, Integer topN) {
        if (attentionAccumulator == null) {return null;}
        int n = topN != null ? topN : 10;
        return attentionAccumulator.currentBriefing(principalId, n);
    }

    @Override
    public DomainActivationResult domainActivation(String tenancyId, String principalId,
                                                   String subgraph1, String subgraph2,
                                                   String from, String to) {
        if (domainActivation == null) {
            return null;
        }
        var query = DomainActivationQuery.between(
                PrincipalId.agent(principalId), tenancyId, subgraph1, subgraph2);
        if (from != null) {query = query.withFrom(Instant.parse(from));}
        if (to != null) {query = query.withTo(Instant.parse(to));}
        return domainActivation.correlate(query).orElse(null);
    }

    @Override
    public GraphAnalyticsResult analytics(String tenancyId, String subgraphId,
                                          Integer kCoreK, Integer staleThresholdDays,
                                          Double lowConfidenceThreshold) {
        int    k             = kCoreK != null ? kCoreK : 2;
        double confThreshold = lowConfidenceThreshold != null ? lowConfidenceThreshold : 0.3;
        return new GraphAnalyticsResult(
                MindMapAnalyzer.orphanNodes(store, subgraphId, tenancyId),
                MindMapAnalyzer.degreeCentrality(store, subgraphId, tenancyId),
                MindMapAnalyzer.subgraphDensity(store, subgraphId, tenancyId),
                MindMapAnalyzer.unvalidatedEdgeRatio(store, subgraphId, tenancyId),
                MindMapAnalyzer.contradictions(store, subgraphId, tenancyId),
                MindMapAnalyzer.lowConfidenceCluster(store, subgraphId, tenancyId, confThreshold),
                MindMapAnalyzer.betweennessCentrality(store, subgraphId, tenancyId),
                MindMapAnalyzer.kCores(store, subgraphId, tenancyId, k));
    }

    @Override
    public java.util.List<ActivitySummary> activities(String tenancyId, String queryType,
                                                      String personName, String placeName,
                                                      Integer limit) {
        if (activityQueryService == null) {
            return java.util.List.of();
        }
        int resultLimit = limit != null ? limit : 20;
        return switch (queryType != null ? queryType : "withPerson") {
            case "withPerson" -> activityQueryService.activitiesWithPerson(personName, resultLimit, tenancyId);
            case "atPlace" -> activityQueryService.activitiesAtPlace(placeName, resultLimit, tenancyId);
            case "lastSeen" -> activityQueryService.lastSeenWith(personName, tenancyId)
                                                   .map(java.util.List::of).orElse(java.util.List.of());
            default -> java.util.List.of();
        };
    }
}

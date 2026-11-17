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
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.intelligence.ActivityQueryService;
import io.casehub.neocortex.mindmap.intelligence.ActivitySummary;
import io.casehub.neocortex.mindmap.intelligence.consolidation.CognitiveAttentionAccumulator;
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
    private final io.casehub.neocortex.cognition.trace.CognitionStateProvider stateProvider;
    private final PipelineSnapshotStore pipelineSnapshotStore;


    public CognitionService(MindMapStore store,
                            CognitiveProfile cognitiveProfile,
                            SnapshotStore snapshotStore,
                            CaseMemoryStore memoryStore,
                            DomainActivation domainActivation,
                            ActivityQueryService activityQueryService,
                            CognitiveAttentionAccumulator attentionAccumulator,
                            io.casehub.neocortex.cognition.trace.CognitionStateProvider stateProvider,
                            PipelineSnapshotStore pipelineSnapshotStore) {
        this.store                 = store;
        this.cognitiveProfile      = cognitiveProfile;
        this.snapshotStore         = snapshotStore;
        this.memoryStore           = memoryStore;
        this.domainActivation      = domainActivation;
        this.activityQueryService  = activityQueryService;
        this.attentionAccumulator  = attentionAccumulator;
        this.stateProvider         = stateProvider;
        this.pipelineSnapshotStore = pipelineSnapshotStore;
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
    public CognitivePipelineDump pipelineDump(String tenancyId, String agentId,
                                              Integer tick, Integer fromTick, Integer toTick) {
        if (tick != null && pipelineSnapshotStore != null) {
            var snapshot = pipelineSnapshotStore.findByTick(tenancyId, agentId, tick);
            return CognitivePipelineDump.single(tenancyId, agentId, snapshot.orElse(null));
        }
        if (fromTick != null && toTick != null && pipelineSnapshotStore != null) {
            var snapshots = pipelineSnapshotStore.find(tenancyId, agentId, fromTick, toTick);
            return CognitivePipelineDump.range(tenancyId, agentId, snapshots);
        }
        if (stateProvider != null) {
            var trace = stateProvider.currentTrace(agentId, tenancyId);
            return CognitivePipelineDump.live(tenancyId, agentId, trace.orElse(null));
        }
        return CognitivePipelineDump.live(tenancyId, agentId, null);
    }

    private static final String REGULATION_COGNITIVE_KIND     = "emotion-regulation-result";
    private static final double DEFAULT_MISCALIBRATION_MARGIN = 0.15;

    @Override
    public PredictionCalibrationResult calibration(String tenancyId, String agentId, Integer minEpisodes) {
        if (store == null || agentId == null) {return null;}
        int min = minEpisodes != null ? minEpisodes : 5;

        var nodes = store.search(io.casehub.neocortex.mindmap.MindMapQuery.of(tenancyId, 500)
                                                                          .withType(io.casehub.neocortex.mindmap.SubgraphTypes.COGNITIVE))
                         .stream()
                         .filter(n -> REGULATION_COGNITIVE_KIND.equals(n.properties().get("cognitiveKind")))
                         .filter(n -> agentId.equals(n.properties().get("agent-id")))
                         .toList();

        var bySource = new java.util.HashMap<String, java.util.List<Double>>();
        for (var node : nodes) {
            String source = node.properties().getOrDefault("strategySource", "UNKNOWN");
            String effStr = node.properties().get("effectiveness");
            if (effStr == null) {continue;}
            try {
                bySource.computeIfAbsent(source, k -> new java.util.ArrayList<>())
                        .add(Double.parseDouble(effStr));
            } catch (NumberFormatException ignored) {}
        }

        var sourceSummaries = new java.util.ArrayList<PredictionCalibrationResult.SourceSummary>();
        var summaryByName   = new java.util.HashMap<String, PredictionCalibrationResult.SourceSummary>();
        for (var entry : bySource.entrySet()) {
            var    scores  = entry.getValue();
            double mean    = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            var    summary = new PredictionCalibrationResult.SourceSummary(entry.getKey(), scores.size(), mean);
            sourceSummaries.add(summary);
            summaryByName.put(entry.getKey(), summary);
        }

        var    predictor         = summaryByName.get("PREDICTOR");
        var    llm               = summaryByName.get("LLM");
        int    predictorEpisodes = predictor != null ? predictor.episodeCount() : 0;
        int    llmEpisodes       = llm != null ? llm.episodeCount() : 0;
        int    totalEpisodes     = sourceSummaries.stream().mapToInt(PredictionCalibrationResult.SourceSummary::episodeCount).sum();
        double predictorMean     = predictor != null ? predictor.meanEffectiveness() : 0.0;
        double llmMean           = llm != null ? llm.meanEffectiveness() : 0.0;
        double delta             = predictorMean - llmMean;

        if (predictorEpisodes < min || llmEpisodes < min) {
            String reason;
            if (predictorEpisodes == 0 && llmEpisodes == 0) {
                reason = "No PREDICTOR or LLM episodes";
            } else if (predictorEpisodes < min && llmEpisodes < min) {
                reason = "Insufficient PREDICTOR (%d) and LLM (%d) episodes (min %d)"
                                 .formatted(predictorEpisodes, llmEpisodes, min);
            } else if (predictorEpisodes < min) {
                reason = "Insufficient PREDICTOR episodes (%d, min %d)"
                                 .formatted(predictorEpisodes, min);
            } else {
                reason = "Insufficient LLM episodes (%d, min %d)"
                                 .formatted(llmEpisodes, min);
            }
            return new PredictionCalibrationResult(sourceSummaries, predictorEpisodes, llmEpisodes,
                                                   totalEpisodes, predictorMean, llmMean, delta, false, true, reason);
        }

        boolean miscalibrated = delta < -DEFAULT_MISCALIBRATION_MARGIN;
        return new PredictionCalibrationResult(sourceSummaries, predictorEpisodes, llmEpisodes,
                                               totalEpisodes, predictorMean, llmMean, delta, miscalibrated, false, null);
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

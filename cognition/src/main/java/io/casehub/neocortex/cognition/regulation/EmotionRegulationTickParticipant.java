package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.appraisal.AppraisalTickParticipant;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.core.CognitiveResourcePool;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.PersonalityFacet;
import io.casehub.neocortex.cognitive.index.RegulationDefaults;
import io.casehub.neocortex.cognitive.index.RelationalSchema;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.OverlayRef;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class EmotionRegulationTickParticipant implements CognitionTickParticipant {

    private static final Logger LOG = Logger.getLogger(EmotionRegulationTickParticipant.class.getName());

    private final           AppraisalTickParticipant         appraisalParticipant;
    private final           CognitionConfig                  config;
    private final @Nullable EmotionRegulationGenerationPhase generationPhase;
    private final @Nullable MindMapStore                     mindMapStore;
    private final @Nullable String                           cognitiveSubgraphId;
    private final @Nullable CognitiveResourcePool            cognitiveResourcePool;
    private final           RegulationMonitor                regulationMonitor;
    private final @Nullable RegulationStrategyPredictor      strategyPredictor;
    private final @Nullable CognitiveDefaultsRegistry        defaultsRegistry;
    private final @Nullable CognitiveLoadEstimator           loadEstimator;
    private final ConcurrentHashMap<String, StrategyFailureSignal> failureSignals = new ConcurrentHashMap<>();

    // Bosse et al. 2010 — homeostatic baseline intensity (ERL_norm = 0.25 on [0,2], normalized to 0.3 on [0,1])
    static final double BASELINE_INTENSITY = 0.3;
    // Bosse et al. 2010 — maximum possible discrepancy from baseline
    static final double D_MAX = 1.0 - BASELINE_INTENSITY;
    // Default willingness to regulate (α_n in Bosse). Personality-dependent: high neuroticism → higher α.
    static final double DEFAULT_ALPHA = 0.5;
    // Noise floor: skip regulation when effort is negligible (cost optimization, not a theoretical gate)
    static final double EFFORT_NOISE_FLOOR = 0.01;
    static final double REGULATION_CONTRIBUTION_RATE = 0.15;
    private static final int HISTORY_LIMIT = 5;

    private final ConcurrentHashMap<String, EmotionRegulationResult> results = new ConcurrentHashMap<>();

    public EmotionRegulationTickParticipant(AppraisalTickParticipant appraisalParticipant,
                                            CognitionConfig config) {
        this(appraisalParticipant, config, null, null, null, null, null, null);
    }

    public EmotionRegulationTickParticipant(AppraisalTickParticipant appraisalParticipant,
                                            CognitionConfig config,
                                            @Nullable EmotionRegulationGenerationPhase generationPhase) {
        this(appraisalParticipant, config, generationPhase, null, null, null, null, null);
    }

    public EmotionRegulationTickParticipant(AppraisalTickParticipant appraisalParticipant,
                                            CognitionConfig config,
                                            @Nullable EmotionRegulationGenerationPhase generationPhase,
                                            @Nullable MindMapStore mindMapStore,
                                            @Nullable String cognitiveSubgraphId) {
        this(appraisalParticipant, config, generationPhase, mindMapStore, cognitiveSubgraphId, null, null, null);
    }

    public EmotionRegulationTickParticipant(AppraisalTickParticipant appraisalParticipant,
                                            CognitionConfig config,
                                            @Nullable EmotionRegulationGenerationPhase generationPhase,
                                            @Nullable MindMapStore mindMapStore,
                                            @Nullable String cognitiveSubgraphId,
                                            @Nullable CognitiveDefaultsRegistry defaultsRegistry,
                                            @Nullable CognitiveLoadEstimator loadEstimator,
                                            @Nullable CognitiveResourcePool cognitiveResourcePool) {
        this.appraisalParticipant  = appraisalParticipant;
        this.config                = config;
        this.generationPhase       = generationPhase;
        this.mindMapStore          = mindMapStore;
        this.cognitiveSubgraphId   = cognitiveSubgraphId;
        this.cognitiveResourcePool = cognitiveResourcePool;
        this.regulationMonitor     = new RegulationMonitor();
        this.defaultsRegistry      = defaultsRegistry;
        this.loadEstimator         = loadEstimator;
        this.strategyPredictor     = (defaultsRegistry != null) ? new RegulationStrategyPredictor() : null;
    }

    @Override
    public void tick(CognitionTickContext context) {
        if (!config.regulationEnabled() || !config.appraisalEnabled()) {return;}

        var appraisal = appraisalParticipant.currentResult(
                context.agentId(), context.tenantId());
        if (appraisal.isEmpty()) {return;}

        if (appraisal.get().emotions().isEmpty()) {return;}

        double maxDiscrepancy = appraisal.get().emotions().stream()
                                         .mapToDouble(e -> Math.abs(e.intensity() - BASELINE_INTENSITY))
                                         .max().orElse(0.0);

        // Bosse et al. 2010: regulation effort v_n = α_n × d / d_max (proportional to discrepancy)
        double depletion = cognitiveResourcePool != null
                ? cognitiveResourcePool.currentDepletion(context.agentId(), context.tenantId()) : 0.0;
        double alpha = DEFAULT_ALPHA * (1.0 - depletion);
        double regulationEffort = alpha * maxDiscrepancy / D_MAX;

        if (regulationEffort < EFFORT_NOISE_FLOOR) {
            if (cognitiveResourcePool != null && defaultsRegistry != null) {
                var poolConfig = defaultsRegistry.forAgentOrDefaults(context.agentId()).cognitiveResourcePoolConfig();
                if (poolConfig != null) cognitiveResourcePool.recordRecovery(context.agentId(), context.tenantId(), poolConfig.recoveryRate());
            }
            return;
        }

        double currentFatigue = depletion;
        var    pendingSignal  = failureSignals.remove(context.agentId() + ":" + context.tenantId());

        if (generationPhase == null) {return;}

        var peakEmotion = appraisal.get().emotions().stream()
                                   .max(java.util.Comparator.comparingDouble(e -> e.intensity()))
                                   .orElse(null);

        // Petter 2025: try personality-driven strategy prediction before LLM
        EmotionRegulationStrategy                            pinnedStrategy = null;
        io.casehub.neocortex.cognitive.index.DispositionAxes axes           = null;
        if (strategyPredictor != null && defaultsRegistry != null && pendingSignal == null) {
            pinnedStrategy = tryPredictStrategy(context, appraisal.get(), currentFatigue);
            if (pinnedStrategy != null) {
                var pinned = pinnedStrategy;
                LOG.fine(() -> String.format("regulation predictor: agent=%s strategy=%s (pinned)",
                                             context.agentId(), pinned));
            } else {
                LOG.fine(() -> String.format("regulation predictor: agent=%s fell-through → LLM",
                                             context.agentId()));
                // Pass personality context to LLM on fall-through
                var defaults = defaultsRegistry.forAgent(context.agentId());
                if (defaults.isPresent() && defaults.get().descriptor() != null) {
                    axes = defaults.get().descriptor().disposition();
                }
            }
        }

        var dominantType = peakEmotion != null ? peakEmotion.type() : null;
        var history = (mindMapStore != null)
                      ? EmotionRegulationMindMapAdapter.loadHistory(
                mindMapStore, context.agentId(), context.tenantId(), HISTORY_LIMIT)
                      : List.<EmotionRegulationResult>of();
        RegulationDefaults rd = null;
        ShameContext shameContext = null;
        if (defaultsRegistry != null) {
            var defs = defaultsRegistry.forAgent(context.agentId());
            if (defs.isPresent()) {
                rd = defs.get().regulationDefaults();
            }
        }
        if (dominantType != null && RegulationStrategyPredictor.emotionCategory(dominantType)
                == RegulationStrategyPredictor.EmotionCategory.SELF_CONSCIOUS) {
            shameContext = assembleShameContext(context, history);
            if (shameContext != null && shameContext.isSurfaceLevel()) {
                pinnedStrategy = EmotionRegulationStrategy.RESPONSE_MODULATION;
            }
        }

        var result = generationPhase.generate(context, appraisal.get(), history,
                                              regulationEffort, currentFatigue, pendingSignal, pinnedStrategy,
                                              axes, dominantType, rd, shameContext);
        if (result != null && result.isSocialStrategy() && result.targetAgentId() != null) {
            var regCtx = context.regulationContext();
            if (regCtx == null || regCtx.interactingAgents() == null
                    || !regCtx.interactingAgents().contains(result.targetAgentId())) {
                var discarded = result;
                LOG.fine(() -> "Discarding social regulation result — targetAgentId '"
                        + discarded.targetAgentId() + "' not in interactingAgents");
                result = null;
            }
        }
        if (result != null) {
            String key = context.agentId() + ":" + context.tenantId();
            results.put(key, result);
            if (cognitiveResourcePool != null) {
                cognitiveResourcePool.recordEffort(context.agentId(), context.tenantId(),
                        regulationEffort * REGULATION_CONTRIBUTION_RATE);
            }
            var signal = regulationMonitor.record(context.agentId(), context.tenantId(), result);
            if (signal != null) {
                failureSignals.put(key, signal);
            }
            if (mindMapStore != null && cognitiveSubgraphId != null) {
                EmotionRegulationMindMapAdapter.write(
                        mindMapStore, context.agentId(), context.tenantId(),
                        cognitiveSubgraphId, result,
                        dominantType != null ? dominantType.name() : null,
                        context.regulationContext());
            }
        } else {
            if (cognitiveResourcePool != null && defaultsRegistry != null) {
                var poolConfig = defaultsRegistry.forAgentOrDefaults(context.agentId()).cognitiveResourcePoolConfig();
                if (poolConfig != null) cognitiveResourcePool.recordRecovery(context.agentId(), context.tenantId(), poolConfig.recoveryRate());
            }
        }
    }

    private @Nullable EmotionRegulationStrategy tryPredictStrategy(
            CognitionTickContext context,
            io.casehub.neocortex.cognition.appraisal.AppraisalResult appraisal,
            double currentFatigue) {
        var defaults = defaultsRegistry.forAgent(context.agentId());
        if (defaults.isEmpty() || defaults.get().descriptor() == null) {return null;}
        var axes = defaults.get().descriptor().disposition();
        if (axes == null) {return null;}
        var rd = defaults.get().regulationDefaults();

        var peakEmotion = appraisal.emotions().stream()
                                   .max(java.util.Comparator.comparingDouble(e -> e.intensity()))
                                   .orElse(null);
        if (peakEmotion == null) {return null;}

        CognitiveLoadLevel loadLevel = (loadEstimator != null)
                                       ? loadEstimator.estimate(context.agentId(), context.tenantId()).level()
                                       : null;

        var prediction = strategyPredictor.predict(
                rd, axes, peakEmotion.type(), peakEmotion.intensity(), currentFatigue, loadLevel);

        if (LOG.isLoggable(java.util.logging.Level.FINE)) {
            if (prediction.isPresent()) {
                LOG.fine(String.format("regulation predictor detail: agent=%s emotion=%s intensity=%.2f → %s (confidence=%.2f)",
                                       context.agentId(), peakEmotion.type(), peakEmotion.intensity(),
                                       prediction.get().strategy(), prediction.get().confidence()));
            } else {
                LOG.fine(String.format("regulation predictor detail: agent=%s emotion=%s intensity=%.2f → fell-through (personality=%s/%s/%s/%s/%s)",
                                       context.agentId(), peakEmotion.type(), peakEmotion.intensity(),
                                       axes.socialOrient(), axes.ruleFollowing(), axes.riskAppetite(),
                                       axes.autonomy(), axes.conflictMode()));
            }
        }

        return prediction.map(RegulationPrediction::strategy).orElse(null);
    }

    private @Nullable ShameContext assembleShameContext(
            CognitionTickContext context,
            List<EmotionRegulationResult> history) {
        int               shameCount = 0;
        java.time.Instant mostRecent = null;
        var               cutoff     = java.time.Instant.now().minus(java.time.Duration.ofMinutes(30));
        for (var entry : history) {
            boolean isSelfConscious = entry.targetEmotions().stream()
                                           .anyMatch(e -> "SHAME".equals(e) || "REMORSE".equals(e));
            if (!isSelfConscious) {continue;}
            shameCount++;
            if (mostRecent == null) {mostRecent = java.time.Instant.now();}
        }

        double closeness = 0.0;
        String role      = null;
        if (context.targetAgentId() != null
            && !context.targetAgentId().equals(context.agentId())
            && mindMapStore != null) {
            var schema = resolveRelationalSchema(
                    context.agentId(), context.targetAgentId(), context.tenantId());
            if (schema != null) {
                closeness = schema.intimacy();
                role      = schema.role();
            }
        }

        double grandiosity = 0.0;
        double callousness = 0.0;
        if (defaultsRegistry != null) {
            var defaults = defaultsRegistry.forAgent(context.agentId());
            if (defaults.isPresent() && defaults.get().personalityProfile() != null) {
                var facets = defaults.get().personalityProfile().facets();
                if (facets != null) {
                    for (var f : facets) {
                        if (PersonalityFacet.GRANDIOSITY.equals(f.facet())) {
                            grandiosity = f.score();
                        } else if (PersonalityFacet.CALLOUSNESS.equals(f.facet())) {
                            callousness = f.score();
                        }
                    }
                }
            }
        }

        return new ShameContext(shameCount, mostRecent, closeness, role, grandiosity, callousness);
    }

    private @Nullable RelationalSchema resolveRelationalSchema(
            String observerAgentId, String targetAgentId, String tenantId) {
        var peopleSg = mindMapStore.listSubgraphs(tenantId).stream()
                                   .filter(sg -> SubgraphTypes.PERSON.equals(sg.type())
                                                 || SubgraphTypes.PERSON.equals(sg.name()))
                                   .findFirst();
        if (peopleSg.isEmpty()) {return null;}

        var nodes = mindMapStore.nodesIn(peopleSg.get().id(), tenantId);

        String targetNodeId = null;
        for (var node : nodes) {
            if (node.traits().contains("overlay")) {continue;}
            if (targetAgentId.equals(node.property("agentId").orElse(null))) {
                targetNodeId = node.id();
                break;
            }
        }
        if (targetNodeId == null) {return null;}

        for (var overlay : nodes) {
            if (!overlay.traits().contains("overlay")) {continue;}
            if (!observerAgentId.equals(overlay.property(OverlayRef.AGENT_ID).orElse(null))) {
                continue;
            }
            var sharedId = OverlayRef.sharedNodeId(overlay).orElse(null);
            if (targetNodeId.equals(sharedId)) {
                return RelationalSchema.fromProperties(overlay.properties());
            }
        }
        return null;
    }


    public Optional<EmotionRegulationResult> currentResult(String agentId, String tenantId) {
        return Optional.ofNullable(results.get(agentId + ":" + tenantId));
    }
}

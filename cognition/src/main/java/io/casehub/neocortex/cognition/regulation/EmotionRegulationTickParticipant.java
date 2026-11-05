package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.appraisal.AppraisalTickParticipant;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.mindmap.MindMapStore;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionRegulationTickParticipant implements CognitionTickParticipant {

    private final           AppraisalTickParticipant         appraisalParticipant;
    private final           CognitionConfig                  config;
    private final @Nullable EmotionRegulationGenerationPhase generationPhase;
    private final @Nullable MindMapStore                     mindMapStore;
    private final @Nullable String                           cognitiveSubgraphId;
    private final           RegulationFatigueTracker         fatigueTracker;
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
    private static final int HISTORY_LIMIT = 5;

    private final ConcurrentHashMap<String, EmotionRegulationResult> results = new ConcurrentHashMap<>();

    public EmotionRegulationTickParticipant(AppraisalTickParticipant appraisalParticipant,
                                            CognitionConfig config) {
        this(appraisalParticipant, config, null, null, null, null, null);
    }

    public EmotionRegulationTickParticipant(AppraisalTickParticipant appraisalParticipant,
                                            CognitionConfig config,
                                            @Nullable EmotionRegulationGenerationPhase generationPhase) {
        this(appraisalParticipant, config, generationPhase, null, null, null, null);
    }

    public EmotionRegulationTickParticipant(AppraisalTickParticipant appraisalParticipant,
                                            CognitionConfig config,
                                            @Nullable EmotionRegulationGenerationPhase generationPhase,
                                            @Nullable MindMapStore mindMapStore,
                                            @Nullable String cognitiveSubgraphId) {
        this(appraisalParticipant, config, generationPhase, mindMapStore, cognitiveSubgraphId, null, null);
    }

    public EmotionRegulationTickParticipant(AppraisalTickParticipant appraisalParticipant,
                                            CognitionConfig config,
                                            @Nullable EmotionRegulationGenerationPhase generationPhase,
                                            @Nullable MindMapStore mindMapStore,
                                            @Nullable String cognitiveSubgraphId,
                                            @Nullable CognitiveDefaultsRegistry defaultsRegistry,
                                            @Nullable CognitiveLoadEstimator loadEstimator) {
        this.appraisalParticipant = appraisalParticipant;
        this.config               = config;
        this.generationPhase      = generationPhase;
        this.mindMapStore         = mindMapStore;
        this.cognitiveSubgraphId  = cognitiveSubgraphId;
        this.fatigueTracker       = new RegulationFatigueTracker();
        this.regulationMonitor    = new RegulationMonitor();
        this.defaultsRegistry     = defaultsRegistry;
        this.loadEstimator        = loadEstimator;
        this.strategyPredictor    = (defaultsRegistry != null) ? new RegulationStrategyPredictor() : null;
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
        double alpha = fatigueTracker.effectiveAlpha(
                context.agentId(), context.tenantId(), DEFAULT_ALPHA);
        double regulationEffort = alpha * maxDiscrepancy / D_MAX;

        if (regulationEffort < EFFORT_NOISE_FLOOR) {
            fatigueTracker.recordRecovery(context.agentId(), context.tenantId());
            return;
        }

        double currentFatigue = fatigueTracker.currentFatigue(context.agentId(), context.tenantId());
        var pendingSignal = failureSignals.remove(context.agentId() + ":" + context.tenantId());

        // Petter 2025: try personality-driven mechanical prediction before LLM
        EmotionRegulationResult result = null;
        if (strategyPredictor != null && defaultsRegistry != null && pendingSignal == null) {
            result = tryMechanicalPrediction(context, appraisal.get(), maxDiscrepancy, currentFatigue);
        }

        if (result == null) {
            if (generationPhase == null) {return;}
            var history = (mindMapStore != null)
                          ? EmotionRegulationMindMapAdapter.loadHistory(
                    mindMapStore, context.agentId(), context.tenantId(), HISTORY_LIMIT)
                          : List.<EmotionRegulationResult>of();
            result = generationPhase.generate(context, appraisal.get(), history,
                    regulationEffort, currentFatigue, pendingSignal);
        }
        if (result != null) {
            String key = context.agentId() + ":" + context.tenantId();
            results.put(key, result);
            fatigueTracker.recordRegulation(context.agentId(), context.tenantId(), regulationEffort);
            var signal = regulationMonitor.record(context.agentId(), context.tenantId(), result);
            if (signal != null) {
                failureSignals.put(key, signal);
            }
            if (mindMapStore != null && cognitiveSubgraphId != null) {
                EmotionRegulationMindMapAdapter.write(
                        mindMapStore, context.agentId(), context.tenantId(),
                        cognitiveSubgraphId, result);
            }
        } else {
            fatigueTracker.recordRecovery(context.agentId(), context.tenantId());
        }
    }

    private @Nullable EmotionRegulationResult tryMechanicalPrediction(
            CognitionTickContext context,
            io.casehub.neocortex.cognition.appraisal.AppraisalResult appraisal,
            double maxDiscrepancy,
            double currentFatigue) {
        var defaults = defaultsRegistry.forAgent(context.agentId());
        if (defaults.isEmpty() || defaults.get().descriptor() == null) return null;
        var axes = defaults.get().descriptor().disposition();
        if (axes == null) return null;

        double peakIntensity = appraisal.emotions().stream()
                .mapToDouble(e -> e.intensity()).max().orElse(0.0);

        CognitiveLoadLevel loadLevel = (loadEstimator != null)
                ? loadEstimator.estimate(context.agentId(), context.tenantId()).level()
                : null;

        var prediction = strategyPredictor.predict(axes, peakIntensity, currentFatigue, loadLevel);
        if (prediction.isEmpty()) return null;

        var targetEmotions = appraisal.emotions().stream()
                .map(e -> e.type().name()).toList();

        return RegulationStrategyPredictor.toResult(
                prediction.get(), targetEmotions, List.of(), peakIntensity);
    }

    public Optional<EmotionRegulationResult> currentResult(String agentId, String tenantId) {
        return Optional.ofNullable(results.get(agentId + ":" + tenantId));
    }
}

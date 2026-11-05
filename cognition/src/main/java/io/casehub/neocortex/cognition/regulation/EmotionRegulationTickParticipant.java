package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.appraisal.AppraisalTickParticipant;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
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
        this(appraisalParticipant, config, null, null, null);
    }

    public EmotionRegulationTickParticipant(AppraisalTickParticipant appraisalParticipant,
                                            CognitionConfig config,
                                            @Nullable EmotionRegulationGenerationPhase generationPhase) {
        this(appraisalParticipant, config, generationPhase, null, null);
    }

    public EmotionRegulationTickParticipant(AppraisalTickParticipant appraisalParticipant,
                                            CognitionConfig config,
                                            @Nullable EmotionRegulationGenerationPhase generationPhase,
                                            @Nullable MindMapStore mindMapStore,
                                            @Nullable String cognitiveSubgraphId) {
        this.appraisalParticipant = appraisalParticipant;
        this.config               = config;
        this.generationPhase      = generationPhase;
        this.mindMapStore         = mindMapStore;
        this.cognitiveSubgraphId  = cognitiveSubgraphId;
        this.fatigueTracker       = new RegulationFatigueTracker();
        this.regulationMonitor   = new RegulationMonitor();
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

        if (generationPhase == null) {return;}

        var history = (mindMapStore != null)
                      ? EmotionRegulationMindMapAdapter.loadHistory(
                mindMapStore, context.agentId(), context.tenantId(), HISTORY_LIMIT)
                      : List.<EmotionRegulationResult>of();

        double currentFatigue = fatigueTracker.currentFatigue(context.agentId(), context.tenantId());
        var pendingSignal = failureSignals.remove(context.agentId() + ":" + context.tenantId());
        var result = generationPhase.generate(context, appraisal.get(), history,
                regulationEffort, currentFatigue, pendingSignal);
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

    public Optional<EmotionRegulationResult> currentResult(String agentId, String tenantId) {
        return Optional.ofNullable(results.get(agentId + ":" + tenantId));
    }
}

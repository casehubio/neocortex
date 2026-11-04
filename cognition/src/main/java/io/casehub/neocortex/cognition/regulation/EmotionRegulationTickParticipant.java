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

    // Bosse et al. 2010 — homeostatic baseline intensity
    static final         double BASELINE_INTENSITY   = 0.3;
    // Starting threshold for empirical tuning
    static final         double ACTIVATION_THRESHOLD = 0.4;
    private static final int    HISTORY_LIMIT        = 5;

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

        if (maxDiscrepancy <= ACTIVATION_THRESHOLD) {return;}

        if (generationPhase == null) {return;}

        var history = (mindMapStore != null)
                      ? EmotionRegulationMindMapAdapter.loadHistory(
                mindMapStore, context.agentId(), context.tenantId(), HISTORY_LIMIT)
                      : List.<EmotionRegulationResult>of();

        var result = generationPhase.generate(context, appraisal.get(), history);
        if (result != null) {
            String key = context.agentId() + ":" + context.tenantId();
            results.put(key, result);
            if (mindMapStore != null && cognitiveSubgraphId != null) {
                EmotionRegulationMindMapAdapter.write(
                        mindMapStore, context.agentId(), context.tenantId(),
                        cognitiveSubgraphId, result);
            }
        }
    }

    public Optional<EmotionRegulationResult> currentResult(String agentId, String tenantId) {
        return Optional.ofNullable(results.get(agentId + ":" + tenantId));
    }
}

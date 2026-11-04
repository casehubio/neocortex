package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.appraisal.AppraisalTickParticipant;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionRegulationTickParticipant implements CognitionTickParticipant {

    private final AppraisalTickParticipant appraisalParticipant;
    private final CognitionConfig config;

    // Bosse et al. 2010 — homeostatic baseline intensity
    static final double BASELINE_INTENSITY = 0.3;
    // Starting threshold for empirical tuning
    static final double ACTIVATION_THRESHOLD = 0.4;

    private final ConcurrentHashMap<String, EmotionRegulationResult> results = new ConcurrentHashMap<>();

    public EmotionRegulationTickParticipant(AppraisalTickParticipant appraisalParticipant,
                                            CognitionConfig config) {
        this.appraisalParticipant = appraisalParticipant;
        this.config = config;
    }

    @Override
    public void tick(CognitionTickContext context) {
        if (!config.regulationEnabled() || !config.appraisalEnabled()) return;

        var appraisal = appraisalParticipant.currentResult(
                context.agentId(), context.tenantId());
        if (appraisal.isEmpty()) return;

        if (appraisal.get().emotions().isEmpty()) return;

        double maxDiscrepancy = appraisal.get().emotions().stream()
                .mapToDouble(e -> Math.abs(e.intensity() - BASELINE_INTENSITY))
                .max().orElse(0.0);

        if (maxDiscrepancy <= ACTIVATION_THRESHOLD) return;

        // LLM generation call — wired in Task 4
        var result = generateRegulation(context, appraisal.get());
        if (result != null) {
            String key = context.agentId() + ":" + context.tenantId();
            results.put(key, result);
        }
    }

    public Optional<EmotionRegulationResult> currentResult(String agentId, String tenantId) {
        return Optional.ofNullable(results.get(agentId + ":" + tenantId));
    }

    EmotionRegulationResult generateRegulation(CognitionTickContext context,
                                                io.casehub.neocortex.cognition.appraisal.AppraisalResult appraisal) {
        return null;
    }
}

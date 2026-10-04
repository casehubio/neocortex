package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodSignal;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AppraisalTickParticipant implements CognitionTickParticipant {

    private final SalienceStrategy salienceStrategy;
    private final AppraisalStrategy appraisalStrategy;
    private final DriveOrchestrator driveOrchestrator;
    private final MoodOrchestrator moodOrchestrator;
    private final @Nullable CognitiveDefaultsRegistry defaultsRegistry;
    private final CognitionConfig config;

    private final ConcurrentHashMap<String, AppraisalResult> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HabituationState> habituationStates = new ConcurrentHashMap<>();

    public AppraisalTickParticipant(
            SalienceStrategy salienceStrategy,
            AppraisalStrategy appraisalStrategy,
            DriveOrchestrator driveOrchestrator,
            MoodOrchestrator moodOrchestrator,
            @Nullable CognitiveDefaultsRegistry defaultsRegistry,
            CognitionConfig config) {
        this.salienceStrategy = salienceStrategy;
        this.appraisalStrategy = appraisalStrategy;
        this.driveOrchestrator = driveOrchestrator;
        this.moodOrchestrator = moodOrchestrator;
        this.defaultsRegistry = defaultsRegistry;
        this.config = config;
    }

    @Override
    public void tick(CognitionTickContext context) {
        if (!config.appraisalEnabled()) return;

        var agentKey = context.agentId() + ":" + context.tenantId();
        var observation = context.observation();

        var profileOpt = driveOrchestrator.currentDrives(context.agentId(), context.tenantId());
        if (profileOpt.isEmpty()) return;
        var drives = profileOpt.get().allDrives();

        var mood = moodOrchestrator.currentMood(context.agentId(), context.tenantId())
                .orElse(null);

        var defaults = defaultsRegistry != null
                ? defaultsRegistry.forAgentOrDefaults(context.agentId()) : null;
        var weights = defaults != null ? defaults.appraisalWeights() : null;
        HabituationConfig habConfig = null; // wired by B5T1 via CognitiveDefaults.habituationConfig()
        var habituation = habituationStates.getOrDefault(agentKey, HabituationState.empty());

        PerceivedSituation situation;
        if (config.salienceEnabled() && observation != null) {
            situation = salienceStrategy.perceive(
                    new SalienceContext(observation, drives, mood, List.of(), List.of()));
        } else if (observation != null) {
            situation = PerceivedSituation.passThrough(observation);
        } else {
            return;
        }

        var result = appraisalStrategy.appraise(
                new AppraisalContext(situation, drives, weights, habConfig, habituation, mood));

        results.put(agentKey, result);
        habituationStates.put(agentKey, result.updatedHabituation());

        bridgeToMood(result, context);
    }

    public Optional<AppraisalResult> currentResult(String agentId, String tenantId) {
        return Optional.ofNullable(results.get(agentId + ":" + tenantId));
    }

    private void bridgeToMood(AppraisalResult result, CognitionTickContext context) {
        if (result.emotions().isEmpty()) return;

        double totalIntensity = result.emotions().stream()
                .mapToDouble(CognitiveEmotion::intensity).sum();
        if (totalIntensity <= 0) return;

        double p = result.emotions().stream()
                .mapToDouble(e -> e.intensity() * e.pad().pleasure()).sum() / totalIntensity;
        double a = result.emotions().stream()
                .mapToDouble(e -> e.intensity() * e.pad().arousal()).sum() / totalIntensity;
        double d = result.emotions().stream()
                .mapToDouble(e -> e.intensity() * e.pad().dominance()).sum() / totalIntensity;

        moodOrchestrator.record(
                new MoodSignal.DirectShift(clamp(p), clamp(a), clamp(d), "appraisal-emotions"),
                context.agentId(), context.tenantId());
    }

    private static double clamp(double value) {
        return Math.max(-2.0, Math.min(2.0, value));
    }
}

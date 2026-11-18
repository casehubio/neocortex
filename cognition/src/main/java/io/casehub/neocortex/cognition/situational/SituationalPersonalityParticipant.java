package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
import io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults;
import io.casehub.neocortex.cognitive.index.PersonalityStateSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class SituationalPersonalityParticipant implements CognitionTickParticipant {



    private final PersonalityStateService stateService;
    private final SituationalDetector     detector;
    private final PersonalityStateIntegrator integrator;
    private final SituationalPersonalityConfig config;
    private final EffectiveCognitiveDefaults effectiveDefaults;
    private final AppraisalAccessor appraisalAccessor;
    private final MoodAccessor moodAccessor;
    private final CognitiveJournal journal;
    private final Clock clock;

    private final java.util.concurrent.ConcurrentHashMap<String, DetectionResult> lastResults = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.time.Instant> lastResultAccess = new java.util.concurrent.ConcurrentHashMap<>();

    public SituationalPersonalityParticipant(
            PersonalityStateService stateService,
            SituationalDetector detector,
            PersonalityStateIntegrator integrator,
            SituationalPersonalityConfig config,
            EffectiveCognitiveDefaults effectiveDefaults,
            AppraisalAccessor appraisalAccessor,
            MoodAccessor moodAccessor,
            CognitiveJournal journal,
            Clock clock) {
        this.stateService = stateService;
        this.detector = detector;
        this.integrator = integrator;
        this.config = config;
        this.effectiveDefaults = effectiveDefaults;
        this.appraisalAccessor = appraisalAccessor;
        this.moodAccessor = moodAccessor;
        this.journal = journal;
        this.clock = clock;
    }

    @Override
    public void tick(CognitionTickContext context) {
        var descriptor = context.descriptor();
        if (descriptor == null || descriptor.disposition() == null) return;

        var agentId = context.agentId();
        var tenantId = context.tenantId();
        var now = clock.instant();

        var evidence = buildEvidence(agentId, tenantId, context);
        var profiles = descriptor.disposition().situationalProfiles();
        var deltas = SituationalDeltaConverter.toSituationalDeltas(profiles);
        String provenance = profiles.isEmpty() ? "none" : profiles.getFirst().provenance();
        var agentKey = agentId + ":" + tenantId;
        var previousResult = lastResults.getOrDefault(agentKey, DetectionResult.initial());

        var state = stateService.transition(tenantId, agentId, now, st -> {
            var advanced = integrator.advance(st, now);
            double dtSeconds = Duration.between(st.cursorTime(), now).toMillis() / 1000.0;
            if (dtSeconds < 0) dtSeconds = 0;
            var detection = detector.detect(evidence, previousResult, dtSeconds);
            var withDetection = advanced.withDetection(detection);
            var compositeDelta = computeCompositeDelta(detection, deltas);
            boolean hasWinner = !detection.winners().isEmpty();
            double maxIntensity = detection.intensities().values().stream()
                    .mapToDouble(Double::doubleValue).max().orElse(0);
            boolean evidenceSustained = hasWinner && maxIntensity > config.dynamics().epsilonPublish();
            if (!compositeDelta.isEmpty() && hasWinner) {
                return integrator.pull(withDetection, compositeDelta, maxIntensity, evidenceSustained, now);
            }
            return withDetection;
        });

        var finalDetection = new DetectionResult(state.intensities(), state.engaged(), state.winners(),
                Map.of(), Map.of(), Map.of(), Map.of());
        lastResults.put(agentKey, finalDetection);
        lastResultAccess.put(agentKey, clock.instant());
        var compositeDelta = computeCompositeDelta(finalDetection, deltas);

        journal.record(new CognitiveEntry.SituationalModulation(
                tenantId, agentId,
                context.tickEventRef() != null ? context.tickEventRef()
                        : io.casehub.neocortex.cognition.journal.EventRef.tick(0),
                now,
                toStringKeyMap(finalDetection.intensities()),
                winnerName(finalDetection, SituationalMode.Group.AFFECTIVE),
                winnerName(finalDetection, SituationalMode.Group.SOCIAL),
                compositeDelta,
                0,
                provenance));
    }

    public int evictIdle(java.time.Duration maxIdle) {
        var cutoff = clock.instant().minus(maxIdle);
        var stale = new java.util.ArrayList<String>();
        lastResultAccess.forEach((key, time) -> {
            if (time.isBefore(cutoff)) stale.add(key);
        });
        for (var key : stale) {
            lastResults.remove(key);
            lastResultAccess.remove(key);
        }
        return stale.size();
    }

    private SituationalEvidence buildEvidence(String agentId, String tenantId, CognitionTickContext context) {
        var builder = SituationalEvidence.builder();
        builder.situationInputs(appraisalAccessor.currentInputActivations(agentId, tenantId));
        builder.secDimensions(appraisalAccessor.currentSecDimensions(agentId, tenantId));
        builder.mood(
                moodAccessor.pleasure(agentId, tenantId),
                moodAccessor.arousal(agentId, tenantId),
                moodAccessor.dominance(agentId, tenantId));
        if (context.targetAgentId() != null) {
            builder.interlocutor(0.0);
        }
        return builder.build();
    }

    private Map<String, Double> computeCompositeDelta(DetectionResult detection,
                                                       Map<SituationalMode, Map<String, Double>> profileDeltas) {
        var composite = new LinkedHashMap<String, Double>();
        for (var group : SituationalMode.Group.values()) {
            var winner = detection.winner(group);
            if (winner == null) continue;
            double intensity = detection.intensity(winner);
            var deltas = profileDeltas.get(winner);
            if (deltas == null) continue;
            for (var entry : deltas.entrySet()) {
                composite.merge(entry.getKey(), entry.getValue() * intensity, Double::sum);
            }
        }
        double sMax = config.dynamics().sMax();
        composite.replaceAll((k, v) -> Math.max(-sMax, Math.min(sMax, v)));
        return Map.copyOf(composite);
    }


    private static Map<String, Double> toStringKeyMap(Map<SituationalMode, Double> map) {
        var result = new LinkedHashMap<String, Double>();
        for (var entry : map.entrySet()) {
            result.put(entry.getKey().name(), entry.getValue());
        }
        return Map.copyOf(result);
    }

    private static String winnerName(DetectionResult detection, SituationalMode.Group group) {
        var w = detection.winner(group);
        return w != null ? w.name() : null;
    }

    private static String eventRefString(CognitionTickContext context) {
        return context.tickEventRef() != null ? context.tickEventRef().toString() : "tick";
    }

    public interface AppraisalAccessor {
        Map<String, Double> currentInputActivations(String agentId, String tenantId);
        Map<String, Double> currentSecDimensions(String agentId, String tenantId);
    }

    public interface MoodAccessor {
        double pleasure(String agentId, String tenantId);
        double arousal(String agentId, String tenantId);
        double dominance(String agentId, String tenantId);
    }
}

package io.casehub.neocortex.cognition.gut;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryScanRequest;
import io.casehub.neocortex.memory.experience.ExperienceQuery;
import io.casehub.neocortex.memory.mood.MoodState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GutFeelingParticipant implements CognitionTickParticipant {

    private final @Nullable CaseMemoryStore memoryStore;
    private final @Nullable MoodOrchestrator moodOrchestrator;
    private final ConcurrentHashMap<String, GutFeeling> results = new ConcurrentHashMap<>();

    public GutFeelingParticipant(
            @Nullable CaseMemoryStore memoryStore,
            @Nullable MoodOrchestrator moodOrchestrator) {
        this.memoryStore = memoryStore;
        this.moodOrchestrator = moodOrchestrator;
    }

    @Override
    public void tick(CognitionTickContext context) {
        var agentKey = context.agentId() + ":" + context.tenantId();
        results.remove(agentKey);

        if (memoryStore == null) return;

        String agentId = context.agentId();
        String tenantId = context.tenantId();
        String observation = context.observation();

        if (observation != null && !observation.isBlank()) {
            var query = ExperienceQuery.search(agentId, tenantId, observation)
                .withLimit(3);
            List<Memory> matches = memoryStore.query(query);

            List<Memory> withPad = matches.stream()
                .filter(m -> m.pleasure() != null)
                .toList();

            if (!withPad.isEmpty()) {
                double meanPleasure = withPad.stream()
                    .mapToDouble(Memory::pleasure).average().orElse(0.0);
                double intensity = Math.min(1.0, Math.abs(meanPleasure));

                GutValence valence = meanPleasure > 0.1 ? GutValence.APPROACH
                    : meanPleasure < -0.1 ? GutValence.AVOID
                    : GutValence.CAUTIOUS;

                String description = withPad.stream()
                    .map(Memory::text)
                    .filter(t -> t != null && !t.isBlank())
                    .reduce((a, b) -> a + ", " + b)
                    .map(s -> s.length() > 100 ? s.substring(0, 100) : s)
                    .orElse(null);

                results.put(agentKey, new GutFeeling(valence, intensity, description));
                return;
            }
        }

        if (moodOrchestrator == null) return;
        var moodOpt = moodOrchestrator.currentMood(agentId, tenantId);
        if (moodOpt.isEmpty()) return;
        var mood = moodOpt.get();

        var affectMemories = memoryStore.scan(
            new MemoryScanRequest(tenantId, "affect", null, null, 50, null));
        var affectWithPad = affectMemories.stream()
            .filter(m -> m.pleasure() != null && m.arousal() != null && m.dominance() != null)
            .toList();
        if (affectWithPad.isEmpty()) return;

        double centroidP = affectWithPad.stream().mapToDouble(Memory::pleasure).average().orElse(0.0);
        double centroidA = affectWithPad.stream().mapToDouble(Memory::arousal).average().orElse(0.0);
        double centroidD = affectWithPad.stream().mapToDouble(Memory::dominance).average().orElse(0.0);

        double distance = Math.sqrt(
            Math.pow(mood.pleasure() - centroidP, 2) +
            Math.pow(mood.arousal() - centroidA, 2) +
            Math.pow(mood.dominance() - centroidD, 2));

        if (distance < 0.3) return;

        GutValence fallbackValence = mood.pleasure() > 0.1 ? GutValence.APPROACH
            : mood.pleasure() < -0.1 ? GutValence.AVOID
            : GutValence.CAUTIOUS;
        double fallbackIntensity = Math.min(1.0, distance);
        String fallbackDescription = describePadQuadrant(mood.pleasure(), mood.arousal());

        results.put(agentKey, new GutFeeling(fallbackValence, fallbackIntensity, fallbackDescription));
    }

    public Optional<GutFeeling> currentResult(String agentId, String tenantId) {
        return Optional.ofNullable(results.get(agentId + ":" + tenantId));
    }

    private static String describePadQuadrant(double pleasure, double arousal) {
        String valenceLabel = pleasure > 0 ? "positive" : "negative";
        String arousalLabel = arousal > 0.3 ? "high-arousal" : arousal < -0.3 ? "low-arousal" : "moderate";
        return arousalLabel + " " + valenceLabel + " states";
    }
}

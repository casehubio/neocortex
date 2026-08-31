package io.casehub.neocortex.memory.mood;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MoodModulatedRetrieval {

    private static final double HALF_LIFE_HOURS = 168.0;
    private static final double MAX_PAD_DISTANCE = Math.sqrt(12.0);

    private MoodModulatedRetrieval() {}

    public static List<Memory> reweight(List<Memory> memories,
            PersonalityWeights weights, MoodState currentMood,
            double moodInfluence, Instant now) {
        Objects.requireNonNull(memories, "memories required");
        Objects.requireNonNull(weights, "weights required");
        Objects.requireNonNull(currentMood, "currentMood required");
        Objects.requireNonNull(now, "now required");
        if (moodInfluence < 0.0 || moodInfluence > 1.0)
            throw new IllegalArgumentException("moodInfluence must be in [0, 1], got " + moodInfluence);

        if (memories.isEmpty()) return List.of();

        return memories.stream()
            .sorted(Comparator.comparingDouble(
                (Memory m) -> score(m, weights, currentMood, moodInfluence, now)).reversed())
            .toList();
    }

    private static double score(Memory memory, PersonalityWeights weights,
            MoodState mood, double moodInfluence, Instant now) {
        double recency         = recencyDecay(memory.createdAt(), now);
        double confidenceValue = memory.confidence() != null ? memory.confidence().value() : 1.0;
        double domainWeight    = weights.getWeight(memory.domain());
        double moodFactor = moodFactor(memory, mood, moodInfluence);
        return recency * confidenceValue * domainWeight * moodFactor;
    }

    private static double moodFactor(Memory memory, MoodState mood, double moodInfluence) {
        if (moodInfluence == 0.0) return 1.0;

        Double p = memory.pleasure();
        Double a = memory.arousal();
        Double d = memory.dominance();

        if (p == null && a == null && d == null) {
            var attrs = memory.attributes();
            String pStr = attrs.get(MoodAttributeKeys.PLEASURE);
            String aStr = attrs.get(MoodAttributeKeys.AROUSAL);
            String dStr = attrs.get(MoodAttributeKeys.DOMINANCE);
            if (pStr == null && aStr == null && dStr == null) return 1.0;
            p = pStr != null ? Double.parseDouble(pStr) : 0.0;
            a = aStr != null ? Double.parseDouble(aStr) : 0.0;
            d = dStr != null ? Double.parseDouble(dStr) : 0.0;
        }

        double mp = p != null ? p : 0.0;
        double ma = a != null ? a : 0.0;
        double md = d != null ? d : 0.0;

        double dp = mood.pleasure() - mp;
        double da = mood.arousal() - ma;
        double dd = mood.dominance() - md;
        double distance = Math.sqrt(dp * dp + da * da + dd * dd);
        double alignment = 1.0 - distance / MAX_PAD_DISTANCE;

        return 1.0 + moodInfluence * (alignment - 0.5);
    }

    private static double recencyDecay(Instant createdAt, Instant now) {
        if (createdAt == null) return 0.5;
        double hoursElapsed = Duration.between(createdAt, now).toMillis() / 3_600_000.0;
        if (hoursElapsed < 0) hoursElapsed = 0;
        return Math.exp(-hoursElapsed / HALF_LIFE_HOURS);
    }
}

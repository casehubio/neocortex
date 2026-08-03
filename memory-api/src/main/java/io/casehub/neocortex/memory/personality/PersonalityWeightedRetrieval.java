package io.casehub.neocortex.memory.personality;

import io.casehub.neocortex.memory.Memory;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class PersonalityWeightedRetrieval {

    private static final double HALF_LIFE_HOURS = 168.0;

    private PersonalityWeightedRetrieval() {}

    public static List<Memory> reweight(List<Memory> memories,
            PersonalityWeights weights, Instant now) {
        if (memories.isEmpty()) return List.of();

        return memories.stream()
            .sorted(Comparator.comparingDouble(
                (Memory m) -> score(m, weights, now)).reversed())
            .toList();
    }

    private static double score(Memory memory, PersonalityWeights weights, Instant now) {
        double recency = recencyDecay(memory.createdAt(), now);
        double importance = memory.importance() != null ? memory.importance() : 1.0;
        double domainWeight = weights.getWeight(memory.domain());
        return recency * importance * domainWeight;
    }

    private static double recencyDecay(Instant createdAt, Instant now) {
        if (createdAt == null) return 0.5;
        double hoursElapsed = Duration.between(createdAt, now).toMillis() / 3_600_000.0;
        if (hoursElapsed < 0) hoursElapsed = 0;
        return Math.exp(-hoursElapsed / HALF_LIFE_HOURS);
    }
}

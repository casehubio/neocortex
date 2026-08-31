package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ModulationFactor;
import io.casehub.neocortex.cognitive.ModulationProfile;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.memory.personality.PersonalityWeights;

import java.time.Duration;
import java.time.Instant;

public final class ModulationFactors {

    private static final double MAX_PAD_DISTANCE = Math.sqrt(12.0);

    private ModulationFactors() {}

    public static <T> ModulationFactor<T> recencyDecay(Duration halfLife, Instant now) {
        double halfLifeHours = halfLife.toHours();
        return (item, profile) -> {
            Instant ts = profile.timestamp().apply(item);
            if (ts == null) return 0.5;
            double hoursElapsed = Duration.between(ts, now).toMillis() / 3_600_000.0;
            if (hoursElapsed < 0) hoursElapsed = 0;
            return Math.exp(-hoursElapsed / halfLifeHours);
        };
    }

    public static <T> ModulationFactor<T> confidenceWeight() {
        return (item, profile) -> {
            Confidence conf = profile.confidence().apply(item);
            return conf != null ? conf.value() : 1.0;
        };
    }

    public static <T> ModulationFactor<T> moodCongruence(MoodState mood, double influence) {
        if (influence < 0.0 || influence > 1.0)
            throw new IllegalArgumentException("influence must be in [0, 1], got " + influence);
        if (influence == 0.0) return (item, profile) -> 1.0;
        return (item, profile) -> {
            Double p = profile.pleasure().apply(item);
            Double a = profile.arousal().apply(item);
            Double d = profile.dominance().apply(item);
            if (p == null && a == null && d == null) return 1.0;
            double mp = p != null ? p : 0.0;
            double ma = a != null ? a : 0.0;
            double md = d != null ? d : 0.0;
            double dp = mood.pleasure() - mp;
            double da = mood.arousal() - ma;
            double dd = mood.dominance() - md;
            double distance = Math.sqrt(dp * dp + da * da + dd * dd);
            double alignment = 1.0 - distance / MAX_PAD_DISTANCE;
            return 1.0 + influence * (alignment - 0.5);
        };
    }

    public static ModulationFactor<Memory> domainWeight(PersonalityWeights weights) {
        return (item, profile) -> weights.getWeight(item.domain());
    }
}

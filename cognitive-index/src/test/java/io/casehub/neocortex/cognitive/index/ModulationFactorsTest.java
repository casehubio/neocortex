package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ModulationFactor;
import io.casehub.neocortex.cognitive.ModulationProfile;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModulationFactorsTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private static final ModulationProfile<TestItem> PROFILE = new ModulationProfile<>(
        TestItem::confidence,
        TestItem::pleasure,
        TestItem::arousal,
        TestItem::dominance,
        TestItem::timestamp
    );

    @Test
    void recencyDecayRecentHigherThanOld() {
        var recent = new TestItem(Confidence.unknown(1.0), null, null, null,
            NOW.minus(1, ChronoUnit.HOURS));
        var old = new TestItem(Confidence.unknown(1.0), null, null, null,
            NOW.minus(72, ChronoUnit.HOURS));

        ModulationFactor<TestItem> factor =
            ModulationFactors.recencyDecay(Duration.ofDays(7), NOW);

        assertThat(factor.apply(recent, PROFILE))
            .isGreaterThan(factor.apply(old, PROFILE));
    }

    @Test
    void recencyDecayNullTimestampReturnsHalf() {
        var item = new TestItem(Confidence.unknown(1.0), null, null, null, null);
        ModulationFactor<TestItem> factor =
            ModulationFactors.recencyDecay(Duration.ofDays(7), NOW);
        assertThat(factor.apply(item, PROFILE)).isEqualTo(0.5);
    }

    @Test
    void confidenceWeightHigherConfidenceScoresHigher() {
        var high = new TestItem(Confidence.unknown(0.9), null, null, null, NOW);
        var low = new TestItem(Confidence.unknown(0.2), null, null, null, NOW);
        ModulationFactor<TestItem> factor = ModulationFactors.confidenceWeight();
        assertThat(factor.apply(high, PROFILE))
            .isGreaterThan(factor.apply(low, PROFILE));
    }

    @Test
    void confidenceWeightNullConfidenceReturnsOne() {
        var item = new TestItem(null, null, null, null, NOW);
        ModulationFactor<TestItem> factor = ModulationFactors.confidenceWeight();
        assertThat(factor.apply(item, PROFILE)).isEqualTo(1.0);
    }

    @Test
    void moodCongruenceAlignedItemsScoreHigher() {
        var aligned = new TestItem(Confidence.unknown(1.0), 0.8, 0.5, 0.3, NOW);
        var misaligned = new TestItem(Confidence.unknown(1.0), -0.8, -0.5, -0.3, NOW);
        MoodState mood = new MoodState("agent", "t1", NOW, 0.8, 0.5, 0.3, "test", null, Map.of());

        ModulationFactor<TestItem> factor =
            ModulationFactors.moodCongruence(mood, 0.8);

        assertThat(factor.apply(aligned, PROFILE))
            .isGreaterThan(factor.apply(misaligned, PROFILE));
    }

    @Test
    void moodCongruenceNoPadReturnsOne() {
        var item = new TestItem(Confidence.unknown(1.0), null, null, null, NOW);
        MoodState mood = new MoodState("agent", "t1", NOW, 0.5, 0.5, 0.5, "test", null, Map.of());
        ModulationFactor<TestItem> factor =
            ModulationFactors.moodCongruence(mood, 0.8);
        assertThat(factor.apply(item, PROFILE)).isEqualTo(1.0);
    }

    @Test
    void moodCongruenceZeroInfluenceReturnsOne() {
        var item = new TestItem(Confidence.unknown(1.0), 0.5, 0.5, 0.5, NOW);
        MoodState mood = new MoodState("agent", "t1", NOW, -0.5, -0.5, -0.5, "test", null, Map.of());
        ModulationFactor<TestItem> factor =
            ModulationFactors.moodCongruence(mood, 0.0);
        assertThat(factor.apply(item, PROFILE)).isEqualTo(1.0);
    }

    @Test
    void domainWeightWeightedDomainScoresHigher() {
        var experience = memory(new MemoryDomain("experience"));
        var reflection = memory(new MemoryDomain("reflection"));
        PersonalityWeights weights = new PersonalityWeights(
            Map.of(new MemoryDomain("experience"), 3.0));
        ModulationFactor<Memory> factor =
            ModulationFactors.domainWeight(weights);
        assertThat(factor.apply(experience, ModulationProfiles.MEMORY))
            .isGreaterThan(factor.apply(reflection, ModulationProfiles.MEMORY));
    }

    @Test
    void domainWeightUnweightedDefaultsToOne() {
        var item = memory(new MemoryDomain("observation"));
        PersonalityWeights weights = new PersonalityWeights(
            Map.of(new MemoryDomain("experience"), 2.0));
        ModulationFactor<Memory> factor =
            ModulationFactors.domainWeight(weights);
        assertThat(factor.apply(item, ModulationProfiles.MEMORY)).isEqualTo(1.0);
    }

    private static Memory memory(MemoryDomain domain) {
        return new Memory("m1", "e1", domain, "t1", null, "text",
            Map.of(), NOW, Confidence.unknown(1.0), null, null, null);
    }

    record TestItem(Confidence confidence, Double pleasure,
                    Double arousal, Double dominance, Instant timestamp) {}
}

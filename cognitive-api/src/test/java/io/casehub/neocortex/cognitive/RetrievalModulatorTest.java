package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalModulatorTest {

    private static final ModulationProfile<TestItem> PROFILE = new ModulationProfile<>(
        TestItem::confidence,
        TestItem::pleasure,
        TestItem::arousal,
        TestItem::dominance,
        TestItem::timestamp
    );

    @Test
    void emptyListReturnsEmpty() {
        List<TestItem> result = RetrievalModulator.modulate(
            List.of(), PROFILE, List.of((item, profile) -> 1.0));
        assertThat(result).isEmpty();
    }

    @Test
    void singleFactorSortsByThatFactor() {
        var a = new TestItem("a", 0.9, null, null, null, Instant.now());
        var b = new TestItem("b", 0.1, null, null, null, Instant.now());
        var c = new TestItem("c", 0.5, null, null, null, Instant.now());

        ModulationFactor<TestItem> byConfidence =
            (item, profile) -> {
                Confidence conf = profile.confidence().apply(item);
                return conf != null ? conf.value() : 1.0;
            };

        var result = RetrievalModulator.modulate(List.of(b, c, a), PROFILE, List.of(byConfidence));
        assertThat(result).extracting(TestItem::name).containsExactly("a", "c", "b");
    }

    @Test
    void compositeScoreMultipliesFactors() {
        var high = new TestItem("high", 0.9, null, null, null, Instant.now());
        var low = new TestItem("low", 0.1, null, null, null, Instant.now());

        ModulationFactor<TestItem> factor1 = (item, profile) -> {
            Confidence conf = profile.confidence().apply(item);
            return conf != null ? conf.value() : 1.0;
        };
        ModulationFactor<TestItem> factor2 = (item, profile) -> 2.0;

        var result = RetrievalModulator.modulate(
            List.of(low, high), PROFILE, List.of(factor1, factor2));
        assertThat(result).extracting(TestItem::name).containsExactly("high", "low");
    }

    @Test
    void neutralFactorPreservesOrdering() {
        var a = new TestItem("a", 0.9, null, null, null, Instant.now());
        var b = new TestItem("b", 0.1, null, null, null, Instant.now());

        ModulationFactor<TestItem> scoring =
            (item, profile) -> profile.confidence().apply(item).value();
        ModulationFactor<TestItem> neutral = (item, profile) -> 1.0;

        var withNeutral = RetrievalModulator.modulate(
            List.of(b, a), PROFILE, List.of(scoring, neutral));
        var without = RetrievalModulator.modulate(
            List.of(b, a), PROFILE, List.of(scoring));
        assertThat(withNeutral).extracting(TestItem::name)
            .isEqualTo(without.stream().map(TestItem::name).toList());
    }

    record TestItem(String name, double confValue, Double pleasure,
                    Double arousal, Double dominance, Instant timestamp) {
        Confidence confidence() {
            return Confidence.unknown(confValue);
        }
    }
}

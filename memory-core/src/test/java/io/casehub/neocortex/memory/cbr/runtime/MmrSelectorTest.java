package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MmrSelectorTest {

    private ScoredCbrCase<TestCase> scored(String id, double score,
                                            Map<String, FeatureValue> features) {
        return new ScoredCbrCase<>(new TestCase(features), id, "test-type", score);
    }

    @Test
    void lambdaOne_preservesScoreOrder() {
        var c1 = scored("c1", 0.9, Map.of("a", FeatureValue.string("x")));
        var c2 = scored("c2", 0.8, Map.of("a", FeatureValue.string("y")));
        var c3 = scored("c3", 0.7, Map.of("a", FeatureValue.string("z")));

        var result = MmrSelector.select(
            List.of(c1, c2, c3), 3, 1.0, (a, b) -> 0.0);

        assertThat(result).extracting(ScoredCbrCase::caseId)
            .containsExactly("c1", "c2", "c3");
    }

    @Test
    void lambdaZero_maximizesDiversity() {
        var c1 = scored("c1", 0.9, Map.of("a", FeatureValue.string("x")));
        var c2 = scored("c2", 0.8, Map.of("a", FeatureValue.string("x")));
        var c3 = scored("c3", 0.7, Map.of("a", FeatureValue.string("z")));

        var result = MmrSelector.select(
            List.of(c1, c2, c3), 2, 0.0,
            (a, b) -> a.cbrCase().features().get("a").equals(
                       b.cbrCase().features().get("a")) ? 1.0 : 0.0);

        assertThat(result).extracting(ScoredCbrCase::caseId)
            .containsExactly("c1", "c3");
    }

    @Test
    void topKGreaterThanCandidates_returnsAll() {
        var c1 = scored("c1", 0.9, Map.of());
        var result = MmrSelector.select(List.of(c1), 5, 0.7, (a, b) -> 0.0);
        assertThat(result).hasSize(1);
    }

    @Test
    void resultResortedByScore() {
        var c1 = scored("c1", 0.9, Map.of("a", FeatureValue.string("x")));
        var c2 = scored("c2", 0.5, Map.of("a", FeatureValue.string("y")));
        var c3 = scored("c3", 0.7, Map.of("a", FeatureValue.string("z")));

        var result = MmrSelector.select(
            List.of(c1, c2, c3), 3, 0.7, (a, b) -> 0.0);

        assertThat(result).extracting(ScoredCbrCase::score)
            .isSortedAccordingTo((a, b) -> Double.compare(b, a));
    }

    @Test
    void emptyInput_returnsEmpty() {
        var result = MmrSelector.<TestCase>select(List.of(), 5, 0.7, (a, b) -> 0.0);
        assertThat(result).isEmpty();
    }

    record TestCase(Map<String, FeatureValue> features) implements CbrCase {
        @Override public String cbrType() { return "test"; }
        @Override public String problem() { return "test"; }
        @Override public String solution() { return null; }
        @Override public String outcome() { return null; }
        @Override public Confidence confidence() { return null; }
        @Override public Map<String, FeatureValue> features() { return features; }
        @Override public CbrCase withOutcome(String outcome, Confidence confidence) { return this; }
    }
}

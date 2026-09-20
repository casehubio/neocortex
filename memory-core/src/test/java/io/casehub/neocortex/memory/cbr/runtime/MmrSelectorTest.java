package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MmrSelectorTest {

    private CbrMatch<TestRecord> scored(String id, double score,
                                        Map<String, FeatureValue> features) {
        return new CbrMatch<>(new TestRecord(features), id, "test-type", score);
    }

    @Test
    void lambdaOne_preservesScoreOrder() {
        var c1 = scored("c1", 0.9, Map.of("a", FeatureValue.string("x")));
        var c2 = scored("c2", 0.8, Map.of("a", FeatureValue.string("y")));
        var c3 = scored("c3", 0.7, Map.of("a", FeatureValue.string("z")));

        var result = MmrSelector.select(
            List.of(c1, c2, c3), 3, 1.0, (a, b) -> 0.0);

        assertThat(result).extracting(CbrMatch::caseId)
            .containsExactly("c1", "c2", "c3");
    }

    @Test
    void lambdaZero_maximizesDiversity() {
        var c1 = scored("c1", 0.9, Map.of("a", FeatureValue.string("x")));
        var c2 = scored("c2", 0.8, Map.of("a", FeatureValue.string("x")));
        var c3 = scored("c3", 0.7, Map.of("a", FeatureValue.string("z")));

        var result = MmrSelector.select(
            List.of(c1, c2, c3), 2, 0.0,
            (a, b) -> a.cbrRecord().features().get("a").equals(
                       b.cbrRecord().features().get("a")) ? 1.0 : 0.0);

        assertThat(result).extracting(CbrMatch::caseId)
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

        assertThat(result).extracting(CbrMatch::score)
            .isSortedAccordingTo((a, b) -> Double.compare(b, a));
    }

    @Test
    void emptyInput_returnsEmpty() {
        var result = MmrSelector.<TestRecord>select(List.of(), 5, 0.7, (a, b) -> 0.0);
        assertThat(result).isEmpty();
    }

    record TestRecord(Map<String, FeatureValue> features) implements CbrRecord {
        @Override public String recordType() { return "test"; }
        @Override public String problem()    { return "test"; }
        @Override public String solution()                                            { return null; }
        @Override public String outcome()                                             { return null; }
        @Override public Confidence confidence()                                      { return null; }
        @Override public Map<String, FeatureValue> features()                         { return features; }
        @Override public CbrRecord withOutcome(String outcome, Confidence confidence) { return this; }
    }
}

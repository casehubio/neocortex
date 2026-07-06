package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class CbrSimilarityScorerTest {

    static final CbrFeatureSchema SCHEMA = CbrFeatureSchema.of("test",
        FeatureField.categorical("color"),
        FeatureField.numeric("score", 0.0, 100.0),
        FeatureField.text("label"));

    @Test
    void categoricalExactMatch() {
        double sim = CbrSimilarityScorer.score(
            Map.of("color", "red"), Map.of("color", "red"), Map.of(), SCHEMA);
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void categoricalMismatch() {
        double sim = CbrSimilarityScorer.score(
            Map.of("color", "red"), Map.of("color", "blue"), Map.of(), SCHEMA);
        assertThat(sim).isEqualTo(0.0);
    }

    @Test
    void numericLinearDecay() {
        double sim = CbrSimilarityScorer.score(
            Map.of("score", 80.0), Map.of("score", 60.0), Map.of(), SCHEMA);
        // |80-60| / (100-0) = 0.2, so sim = 1.0 - 0.2 = 0.8
        assertThat(sim).isCloseTo(0.8, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void numericExactMatch() {
        double sim = CbrSimilarityScorer.score(
            Map.of("score", 50.0), Map.of("score", 50.0), Map.of(), SCHEMA);
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void numericMaxDifference() {
        double sim = CbrSimilarityScorer.score(
            Map.of("score", 0.0), Map.of("score", 100.0), Map.of(), SCHEMA);
        assertThat(sim).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void numericRangeInsideScoresOne() {
        double sim = CbrSimilarityScorer.score(
            Map.of("score", NumericRange.of(40.0, 60.0)),
            Map.of("score", 50.0), Map.of(), SCHEMA);
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void numericRangeOutsideDecaysLinearly() {
        // case value 80, range [40,60], field range [0,100]
        // distance to nearest bound = 80-60 = 20, decay = 20/100 = 0.2
        // sim = 1.0 - 0.2 = 0.8
        double sim = CbrSimilarityScorer.score(
            Map.of("score", NumericRange.of(40.0, 60.0)),
            Map.of("score", 80.0), Map.of(), SCHEMA);
        assertThat(sim).isCloseTo(0.8, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void numericRangeFarOutsideClampedToZero() {
        // case value 0, range [90,100], field range [0,100]
        // distance = 90, decay = 90/100 = 0.9, sim = 0.1
        double sim = CbrSimilarityScorer.score(
            Map.of("score", NumericRange.of(90.0, 100.0)),
            Map.of("score", 0.0), Map.of(), SCHEMA);
        assertThat(sim).isCloseTo(0.1, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void textExactMatch() {
        double sim = CbrSimilarityScorer.score(
            Map.of("label", "hello"), Map.of("label", "hello"), Map.of(), SCHEMA);
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void textMismatch() {
        double sim = CbrSimilarityScorer.score(
            Map.of("label", "hello"), Map.of("label", "world"), Map.of(), SCHEMA);
        assertThat(sim).isEqualTo(0.0);
    }

    @Test
    void weightedScoring() {
        // color matches (sim=1.0), score differs (sim=0.8)
        // weight color=2.0, score=1.0
        // weighted = (2*1.0 + 1*0.8) / (2+1) = 2.8/3 ≈ 0.933
        double sim = CbrSimilarityScorer.score(
            Map.of("color", "red", "score", 80.0),
            Map.of("color", "red", "score", 60.0),
            Map.of("color", 2.0, "score", 1.0),
            SCHEMA);
        assertThat(sim).isCloseTo(2.8 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void defaultWeightsAreUniform() {
        // No explicit weights → all fields weight 1.0
        // color matches (1.0), score differs (0.8) → (1+0.8)/2 = 0.9
        double sim = CbrSimilarityScorer.score(
            Map.of("color", "red", "score", 80.0),
            Map.of("color", "red", "score", 60.0),
            Map.of(),
            SCHEMA);
        assertThat(sim).isCloseTo(0.9, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void emptyQueryFeaturesReturnsOne() {
        double sim = CbrSimilarityScorer.score(Map.of(), Map.of("color", "red"), Map.of(), SCHEMA);
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void nullSchemaReturnsOne() {
        double sim = CbrSimilarityScorer.score(
            Map.of("color", "red"), Map.of("color", "red"), Map.of(), null);
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void missingFeatureInCaseScoresZero() {
        double sim = CbrSimilarityScorer.score(
            Map.of("color", "red"), Map.of(), Map.of(), SCHEMA);
        assertThat(sim).isEqualTo(0.0);
    }

    @Test
    void unknownFieldInQueryIgnored() {
        // "unknown" not in schema → skipped, only "color" counts
        double sim = CbrSimilarityScorer.score(
            Map.of("color", "red", "unknown", "val"),
            Map.of("color", "red"),
            Map.of(), SCHEMA);
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void compositeScoreFormula() {
        double composite = CbrSimilarityScorer.compositeScore(0.8, 0.6, 0.3);
        // 0.3 * 0.6 + 0.7 * 0.8 = 0.18 + 0.56 = 0.74
        assertThat(composite).isCloseTo(0.74, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void compositeScoreWithZeroVectorWeight() {
        double composite = CbrSimilarityScorer.compositeScore(0.8, 0.6, 0.0);
        // 0.0 * 0.6 + 1.0 * 0.8 = 0.8
        assertThat(composite).isCloseTo(0.8, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void compositeScoreWithFullVectorWeight() {
        double composite = CbrSimilarityScorer.compositeScore(0.8, 0.6, 1.0);
        // 1.0 * 0.6 + 0.0 * 0.8 = 0.6
        assertThat(composite).isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void numericZeroRangeExactMatch() {
        // Field with min==max → exact match semantics
        var schema = CbrFeatureSchema.of("test",
            FeatureField.numeric("x", 5.0, 5.0));
        double sim = CbrSimilarityScorer.score(
            Map.of("x", 5.0), Map.of("x", 5.0), Map.of(), schema);
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void numericZeroRangeMismatch() {
        var schema = CbrFeatureSchema.of("test",
            FeatureField.numeric("x", 5.0, 5.0));
        double sim = CbrSimilarityScorer.score(
            Map.of("x", 5.0), Map.of("x", 6.0), Map.of(), schema);
        assertThat(sim).isEqualTo(0.0);
    }

    @Test
    void multipleFieldsWeightedAverage() {
        // color match (1.0, w=3), score partial (0.5, w=1), label mismatch (0.0, w=1)
        // weighted = (3*1.0 + 1*0.5 + 1*0.0) / (3+1+1) = 3.5/5 = 0.7
        double sim = CbrSimilarityScorer.score(
            Map.of("color", "red", "score", 50.0, "label", "a"),
            Map.of("color", "red", "score", 0.0, "label", "b"),
            Map.of("color", 3.0, "score", 1.0, "label", 1.0),
            SCHEMA);
        assertThat(sim).isCloseTo(0.7, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void overrideReplacesDefaultTextBehavior() {
        LocalSimilarityFunction prefixMatch = (q, c) ->
            ((String) c).startsWith((String) q) ? 1.0 : 0.0;

        double sim = CbrSimilarityScorer.score(
            Map.of("label", "hel"),
            Map.of("label", "hello world"),
            Map.of(),
            SCHEMA,
            Map.of("label", prefixMatch));
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void overrideForOneFieldDefaultForOthers() {
        LocalSimilarityFunction always1 = (q, c) -> 1.0;

        double sim = CbrSimilarityScorer.score(
            Map.of("color", "red", "label", "a"),
            Map.of("color", "blue", "label", "b"),
            Map.of(),
            SCHEMA,
            Map.of("label", always1));
        // color: exact match miss = 0.0, label: override = 1.0
        // (0.0 + 1.0) / 2 = 0.5
        assertThat(sim).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void emptyOverridesPreservesExistingBehavior() {
        double sim = CbrSimilarityScorer.score(
            Map.of("label", "hello"),
            Map.of("label", "hello"),
            Map.of(),
            SCHEMA,
            Map.of());
        assertThat(sim).isEqualTo(1.0);
    }
}

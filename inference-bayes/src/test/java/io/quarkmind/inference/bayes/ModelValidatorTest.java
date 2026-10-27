package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ModelValidatorTest {

    @Test
    void logLikelihood_perfectModel_returnsZero() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(1.0, 0.0)
            .build();
        var data = List.of(Map.of("X", "a"), Map.of("X", "a"));
        assertThat(ModelValidator.logLikelihood(model, data))
            .isCloseTo(0.0, within(1e-10));
    }

    @Test
    void logLikelihood_uniformModel_correctValue() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5)
            .build();
        var data = List.of(Map.of("X", "a"), Map.of("X", "b"));
        assertThat(ModelValidator.logLikelihood(model, data))
            .isCloseTo(2 * Math.log(0.5), within(1e-10));
    }

    @Test
    void logLikelihood_zeroProbability_negativeInfinity() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(1.0, 0.0)
            .build();
        var data = List.of(Map.of("X", "b"));
        assertThat(ModelValidator.logLikelihood(model, data))
            .isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @Test
    void logLikelihood_conditionalModel_usesParentConfig() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1")
                .given("A")
                .cpt(0.9, 0.1, 0.2, 0.8)
            .build();
        var data = List.of(Map.of("A", "a0", "B", "b0"));
        double expected = Math.log(0.5) + Math.log(0.9);
        assertThat(ModelValidator.logLikelihood(model, data))
            .isCloseTo(expected, within(1e-10));
    }

    @Test
    void logLikelihood_missingValue_skipsVariable() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1")
                .given("A")
                .cpt(0.9, 0.1, 0.2, 0.8)
            .build();
        var data = List.of(Map.of("A", "a0"));
        assertThat(ModelValidator.logLikelihood(model, data))
            .isCloseTo(Math.log(0.5), within(1e-10));
    }

    @Test
    void logLikelihood_emptyData_throws() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        assertThatThrownBy(() -> ModelValidator.logLikelihood(model, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crossValidate_returnsCorrectFoldCount() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        var data = makeUniformData(100);
        var result = ModelValidator.crossValidate(
            structure, data,
            (s, d) -> ParameterLearner.fit(s, d),
            FoldSplitter.kFold(5), 42L);
        assertThat(result.foldCount()).isEqualTo(5);
        assertThat(result.foldScores()).hasSize(5);
    }

    @Test
    void crossValidate_deterministic() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        var data = makeUniformData(100);
        var r1 = ModelValidator.crossValidate(
            structure, data,
            (s, d) -> ParameterLearner.fit(s, d),
            FoldSplitter.kFold(5), 42L);
        var r2 = ModelValidator.crossValidate(
            structure, data,
            (s, d) -> ParameterLearner.fit(s, d),
            FoldSplitter.kFold(5), 42L);
        assertThat(r1.meanLogLikelihood())
            .isCloseTo(r2.meanLogLikelihood(), within(1e-10));
    }

    @Test
    void crossValidate_betterModelHasHigherScore() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1")
                .given("A")
                .cpt(0.5, 0.5, 0.5, 0.5)
            .build();
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < 50; i++) data.add(Map.of("A", "a0", "B", "b0"));
        for (int i = 0; i < 50; i++) data.add(Map.of("A", "a1", "B", "b1"));

        var goodResult = ModelValidator.crossValidate(
            structure, data,
            (s, d) -> ParameterLearner.fit(s, d),
            FoldSplitter.kFold(5), 42L);

        BayesNetwork indepStructure = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1").prior(0.5, 0.5)
            .build();
        var badResult = ModelValidator.crossValidate(
            indepStructure, data,
            (s, d) -> ParameterLearner.fit(s, d),
            FoldSplitter.kFold(5), 42L);

        assertThat(goodResult.meanLogLikelihood())
            .isGreaterThan(badResult.meanLogLikelihood());
    }

    @Test
    void crossValidate_totalObservationsMatchesData() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        var data = makeUniformData(100);
        var result = ModelValidator.crossValidate(
            structure, data,
            (s, d) -> ParameterLearner.fit(s, d),
            FoldSplitter.kFold(5), 42L);
        assertThat(result.totalObservations()).isEqualTo(100);
    }

    @Test
    void crossValidate_holdOut_singleFold() {
        BayesNetwork structure = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        var data = makeUniformData(100);
        var result = ModelValidator.crossValidate(
            structure, data,
            (s, d) -> ParameterLearner.fit(s, d),
            FoldSplitter.holdOut(0.2), 42L);
        assertThat(result.foldCount()).isEqualTo(1);
        assertThat(result.stdLogLikelihood()).isCloseTo(0.0, within(1e-10));
    }

    private static List<Map<String, String>> makeUniformData(int n) {
        var result = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            result.add(Map.of("X", i % 2 == 0 ? "a" : "b"));
        }
        return result;
    }
}

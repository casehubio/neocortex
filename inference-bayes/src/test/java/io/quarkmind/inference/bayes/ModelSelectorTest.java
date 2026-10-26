package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModelSelectorTest {

    @Test
    void freeParameterCount_rootOnly() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        assertThat(ModelSelector.freeParameterCount(model)).isEqualTo(1);
    }

    @Test
    void freeParameterCount_ternaryRoot() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b", "c").prior(0.33, 0.33, 0.34).build();
        assertThat(ModelSelector.freeParameterCount(model)).isEqualTo(2);
    }

    @Test
    void freeParameterCount_withParent() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1").given("A").cpt(0.7, 0.3, 0.3, 0.7)
            .build();
        assertThat(ModelSelector.freeParameterCount(model)).isEqualTo(3);
    }

    @Test
    void freeParameterCount_twoParents() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1").prior(0.5, 0.5)
            .variable("C", "c0", "c1").given("A", "B")
                .cpt(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5)
            .build();
        assertThat(ModelSelector.freeParameterCount(model)).isEqualTo(6);
    }

    @Test
    void bic_formula_isCorrect() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        var data = makeUniformData(100);
        ModelScore score = ModelSelector.bic(model, data);
        double expectedLL = ModelValidator.logLikelihood(model, data);
        double expectedBIC = -2.0 * expectedLL + 1 * Math.log(100);
        assertThat(score.score()).isCloseTo(expectedBIC, within(1e-10));
        assertThat(score.logLikelihood()).isCloseTo(expectedLL, within(1e-10));
        assertThat(score.freeParameters()).isEqualTo(1);
        assertThat(score.observations()).isEqualTo(100);
        assertThat(score.criterion()).isEqualTo("BIC");
    }

    @Test
    void aic_formula_isCorrect() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        var data = makeUniformData(100);
        ModelScore score = ModelSelector.aic(model, data);
        double expectedLL = ModelValidator.logLikelihood(model, data);
        double expectedAIC = -2.0 * expectedLL + 2.0 * 1;
        assertThat(score.score()).isCloseTo(expectedAIC, within(1e-10));
        assertThat(score.criterion()).isEqualTo("AIC");
    }

    @Test
    void bic_penalisesComplexity_moreThanAic_forLargeN() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1").given("A").cpt(0.7, 0.3, 0.3, 0.7)
            .build();
        var data = makeData(1000);
        ModelScore bicScore = ModelSelector.bic(model, data);
        ModelScore aicScore = ModelSelector.aic(model, data);
        assertThat(bicScore.score()).isGreaterThan(aicScore.score());
    }

    @Test
    void compare_prefersSimplierModelWhenFitIsSimilar() {
        BayesNetwork simple = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1").prior(0.5, 0.5)
            .build();
        BayesNetwork complex = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1").given("A").cpt(0.5, 0.5, 0.5, 0.5)
            .build();
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < 50; i++) {
            data.add(Map.of("A", "a0", "B", "b0"));
            data.add(Map.of("A", "a0", "B", "b1"));
            data.add(Map.of("A", "a1", "B", "b0"));
            data.add(Map.of("A", "a1", "B", "b1"));
        }

        ModelComparison comparison = ModelSelector.compare(
            simple, "simple", complex, "complex", data);
        assertThat(comparison.preferredModel()).isEqualTo("simple");
        assertThat(comparison.scoreDifference()).isGreaterThan(0.0);
    }

    @Test
    void compare_prefersDependentModelWhenDependencyExists() {
        BayesNetwork simple = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1").prior(0.5, 0.5)
            .build();
        BayesNetwork dependent = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1").given("A").cpt(0.9, 0.1, 0.1, 0.9)
            .build();
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < 100; i++) data.add(Map.of("A", "a0", "B", "b0"));
        for (int i = 0; i < 100; i++) data.add(Map.of("A", "a1", "B", "b1"));

        ModelComparison comparison = ModelSelector.compare(
            simple, "simple", dependent, "dependent", data);
        assertThat(comparison.preferredModel()).isEqualTo("dependent");
    }

    private static List<Map<String, String>> makeUniformData(int n) {
        var result = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            result.add(Map.of("X", i % 2 == 0 ? "a" : "b"));
        }
        return result;
    }

    private static List<Map<String, String>> makeData(int n) {
        var result = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            result.add(Map.of("A", i % 2 == 0 ? "a0" : "a1",
                              "B", i % 3 == 0 ? "b0" : "b1"));
        }
        return result;
    }
}

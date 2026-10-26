package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HypothesisPriorLearnerTest {

    @Test
    void batch_uniformData_uniformPriors() {
        var priors = HypothesisPriorLearner.fromOutcomes(
            List.of("rush", "expand", "tech", "rush", "expand", "tech"));

        assertEquals(3, priors.size());
        assertEquals(1.0 / 3, priors.get("rush"), 1e-9);
        assertEquals(1.0 / 3, priors.get("expand"), 1e-9);
        assertEquals(1.0 / 3, priors.get("tech"), 1e-9);
    }

    @Test
    void batch_skewedData_skewedPriors() {
        var priors = HypothesisPriorLearner.fromOutcomes(
            List.of("rush", "rush", "rush", "expand", "tech"));

        assertEquals(0.6, priors.get("rush"), 1e-9);
        assertEquals(0.2, priors.get("expand"), 1e-9);
        assertEquals(0.2, priors.get("tech"), 1e-9);
    }

    @Test
    void batch_singleOutcome_fullWeight() {
        var priors = HypothesisPriorLearner.fromOutcomes(
            List.of("rush", "rush", "rush"));

        assertEquals(1, priors.size());
        assertEquals(1.0, priors.get("rush"), 1e-9);
    }

    @Test
    void batch_emptyList_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            HypothesisPriorLearner.fromOutcomes(List.of()));
    }

    @Test
    void batchSmoothed_preventsZeroPriors() {
        var priors = HypothesisPriorLearner.fromOutcomes(
            List.of("rush", "rush", "rush"),
            Set.of("rush", "expand", "tech"),
            1.0);

        assertTrue(priors.get("expand") > 0, "Smoothing should prevent zero priors");
        assertTrue(priors.get("tech") > 0);
        assertTrue(priors.get("rush") > priors.get("expand"),
            "Rush should still dominate with 3 observations + smoothing");
        double sum = priors.values().stream().mapToDouble(d -> d).sum();
        assertEquals(1.0, sum, 1e-9);
    }

    @Test
    void batchSmoothed_uniformWithNoData() {
        var priors = HypothesisPriorLearner.fromOutcomes(
            List.of(),
            Set.of("rush", "expand", "tech"),
            1.0);

        assertEquals(1.0 / 3, priors.get("rush"), 1e-9);
        assertEquals(1.0 / 3, priors.get("expand"), 1e-9);
        assertEquals(1.0 / 3, priors.get("tech"), 1e-9);
    }

    @Test
    void online_sequentialUpdates_convergesToEmpirical() {
        var learner = new HypothesisPriorLearner(
            Set.of("rush", "expand", "tech"), 1.0);

        learner.update("rush");
        learner.update("rush");
        learner.update("rush");
        learner.update("expand");

        Map<String, Double> priors = learner.priors();
        assertTrue(priors.get("rush") > priors.get("expand"));
        assertTrue(priors.get("expand") > priors.get("tech"));
        double sum = priors.values().stream().mapToDouble(d -> d).sum();
        assertEquals(1.0, sum, 1e-9);
    }

    @Test
    void online_noUpdates_uniformFromSmoothing() {
        var learner = new HypothesisPriorLearner(
            Set.of("rush", "expand", "tech"), 1.0);

        Map<String, Double> priors = learner.priors();
        assertEquals(1.0 / 3, priors.get("rush"), 1e-9);
        assertEquals(1.0 / 3, priors.get("expand"), 1e-9);
    }

    @Test
    void online_unknownHypothesis_throws() {
        var learner = new HypothesisPriorLearner(
            Set.of("rush", "expand"), 1.0);

        assertThrows(IllegalArgumentException.class, () ->
            learner.update("tech"));
    }

    @Test
    void integration_learnedPriorsPlugIntoTracker() {
        var priors = HypothesisPriorLearner.fromOutcomes(
            List.of("rush", "rush", "rush", "expand", "tech"));

        JunctionTree tree = BayesNetworkBuilder.create("simple")
            .variable("X", "a", "b")
                .prior(0.5, 0.5)
            .buildJunctionTree();

        var comp = CompositionBuilder.create("test")
            .slot("main", tree)
            .build();

        var builder = HypothesisTracker.builder(comp);
        for (var entry : priors.entrySet()) {
            builder.hypothesis(entry.getKey()).priorWeight(entry.getValue());
        }
        var tracker = builder.build();

        assertEquals(0.6, tracker.weights().get("rush"), 1e-9);
        assertEquals(0.2, tracker.weights().get("expand"), 1e-9);
        assertEquals(0.2, tracker.weights().get("tech"), 1e-9);
    }
}

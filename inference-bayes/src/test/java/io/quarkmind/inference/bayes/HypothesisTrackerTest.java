package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HypothesisTrackerTest {

    private JunctionTree scoutingTree() {
        return BayesNetworkBuilder.create("scouting")
            .variable("observed_units", "gateway", "hatchery", "barracks")
                .prior(1.0 / 3, 1.0 / 3, 1.0 / 3)
            .variable("enemy_strategy", "rush", "expand", "tech")
                .given("observed_units")
                .cpt(0.7, 0.1, 0.2,
                     0.1, 0.7, 0.2,
                     0.2, 0.2, 0.6)
            .buildJunctionTree();
    }

    private JunctionTree buildOrderTree() {
        return BayesNetworkBuilder.create("buildOrder")
            .variable("enemy_strategy", "rush", "expand", "tech")
                .prior(1.0 / 3, 1.0 / 3, 1.0 / 3)
            .variable("build_choice", "defensive", "greedy", "balanced")
                .given("enemy_strategy")
                .cpt(0.8, 0.1, 0.1,
                     0.1, 0.7, 0.2,
                     0.2, 0.2, 0.6)
            .buildJunctionTree();
    }

    private NetworkComposition twoSlotComposition() {
        return CompositionBuilder.create("sc2")
            .slot("scouting", scoutingTree())
            .slot("buildOrder", buildOrderTree())
            .link("scouting", "enemy_strategy").to("buildOrder", "enemy_strategy")
            .build();
    }

    private JunctionTree multiObsTree() {
        return BayesNetworkBuilder.create("multiObs")
            .variable("theory", "A", "B")
                .prior(0.5, 0.5)
            .variable("obs1", "yes", "no")
                .given("theory")
                .cpt(0.8, 0.2,
                     0.2, 0.8)
            .variable("obs2", "yes", "no")
                .given("theory")
                .cpt(0.7, 0.3,
                     0.3, 0.7)
            .variable("obs3", "yes", "no")
                .given("theory")
                .cpt(0.9, 0.1,
                     0.1, 0.9)
            .buildJunctionTree();
    }

    private NetworkComposition multiObsComposition() {
        return CompositionBuilder.create("multiObs")
            .slot("main", multiObsTree())
            .build();
    }

    @Test
    void initialState_uniformWeights() {
        var comp = twoSlotComposition();
        var tracker = HypothesisTracker.builder(comp)
            .hypothesis("rush").priorWeight(0.33)
            .hypothesis("expand").priorWeight(0.33)
            .hypothesis("tech").priorWeight(0.34)
            .build();

        Map<String, Double> weights = tracker.weights();
        assertEquals(3, weights.size());
        assertEquals(0.33, weights.get("rush"), 1e-9);
        assertEquals(0.33, weights.get("expand"), 1e-9);
        assertEquals(0.34, weights.get("tech"), 1e-9);

        assertEquals(3, tracker.viable().size());
        assertNotNull(tracker.leader());
        assertEquals("tech", tracker.leader());
        assertFalse(tracker.isResolved());

        TrackerSnapshot snap = tracker.snapshot();
        assertEquals(0, snap.observationCount());
        assertEquals(3, snap.viable().size());
        assertEquals(HypothesisStatus.VIABLE, snap.statuses().get("rush"));
    }

    @Test
    void builder_rejectsNoHypotheses() {
        var comp = twoSlotComposition();
        assertThrows(IllegalArgumentException.class, () ->
            HypothesisTracker.builder(comp).build());
    }

    @Test
    void builder_rejectsDuplicateNames() {
        var comp = twoSlotComposition();
        assertThrows(IllegalArgumentException.class, () ->
            HypothesisTracker.builder(comp)
                .hypothesis("rush").priorWeight(0.5)
                .hypothesis("rush").priorWeight(0.5)
                .build());
    }

    @Test
    void reset_restoresInitialState() {
        var comp = twoSlotComposition();
        var tracker = HypothesisTracker.builder(comp)
            .hypothesis("rush").priorWeight(0.5)
            .hypothesis("expand").priorWeight(0.5)
            .build();

        tracker.reset();

        assertEquals(0.5, tracker.weights().get("rush"), 1e-9);
        assertEquals(0.5, tracker.weights().get("expand"), 1e-9);
        assertEquals(2, tracker.viable().size());
    }

    @Test
    void twoHypothesis_convergesToFavoredHypothesis() {
        var comp = twoSlotComposition();
        var tracker = HypothesisTracker.builder(comp)
            .hypothesis("rush").priorWeight(0.5)
                .priorOverride("scouting", "enemy_strategy", new double[]{0.8, 0.1, 0.1})
            .hypothesis("expand").priorWeight(0.5)
                .priorOverride("scouting", "enemy_strategy", new double[]{0.1, 0.8, 0.1})
            .build();

        ObservationResult result = tracker.observeHard("scouting", "observed_units", "gateway");

        assertTrue(result.weights().get("rush") > result.weights().get("expand"),
            "Rush should have higher weight after gateway observation");
        assertEquals(2, result.viable().size());
        assertEquals("rush", result.leader());
        assertTrue(result.eliminated().isEmpty());
    }

    @Test
    void hardElimination_zeroLikelihood() {
        JunctionTree normalTree = BayesNetworkBuilder.create("normal")
            .variable("observed", "gateway", "hatch")
                .prior(0.5, 0.5)
            .buildJunctionTree();

        JunctionTree neverGatewayTree = BayesNetworkBuilder.create("noGateway")
            .variable("observed", "gateway", "hatch")
                .prior(0.0, 1.0)
            .buildJunctionTree();

        var comp = CompositionBuilder.create("elim-test")
            .slot("scout", normalTree)
                .variant("never-gateway", neverGatewayTree)
            .build();

        var tracker = HypothesisTracker.builder(comp)
            .hypothesis("normal").priorWeight(0.5)
            .hypothesis("no-gateway").priorWeight(0.5)
                .activateVariant("scout", "never-gateway")
            .eliminationThreshold(0.001)
            .build();

        ObservationResult result = tracker.observeHard("scout", "observed", "gateway");

        assertEquals(1, result.viable().size());
        assertEquals("normal", result.viable().get(0));
        assertEquals(1, result.eliminated().size());
        assertEquals("no-gateway", result.eliminated().get(0));
        assertEquals(1.0, result.weights().get("normal"), 1e-9);
    }

    @Test
    void softElimination_belowThreshold() {
        var comp = multiObsComposition();
        var tracker = HypothesisTracker.builder(comp)
            .hypothesis("theoryA").priorWeight(0.5)
                .priorOverride("main", "theory", new double[]{0.9, 0.1})
            .hypothesis("theoryB").priorWeight(0.5)
                .priorOverride("main", "theory", new double[]{0.1, 0.9})
            .eliminationThreshold(0.15)
            .build();

        tracker.observeHard("main", "obs1", "yes");
        tracker.observeHard("main", "obs2", "yes");
        ObservationResult result = tracker.observeHard("main", "obs3", "yes");

        assertFalse(result.viable().contains("theoryB"),
            "theoryB should be eliminated after consistent theory=A evidence");
        assertTrue(result.viable().contains("theoryA"));
    }

    @Test
    void resolution_leaderExceedsThreshold() {
        var comp = multiObsComposition();
        var tracker = HypothesisTracker.builder(comp)
            .hypothesis("theoryA").priorWeight(0.5)
                .priorOverride("main", "theory", new double[]{0.9, 0.1})
            .hypothesis("theoryB").priorWeight(0.5)
                .priorOverride("main", "theory", new double[]{0.1, 0.9})
            .resolutionThreshold(0.85)
            .eliminationThreshold(0.001)
            .build();

        tracker.observeHard("main", "obs1", "yes");
        tracker.observeHard("main", "obs2", "yes");
        ObservationResult result = tracker.observeHard("main", "obs3", "yes");

        assertTrue(result.resolved(), "Should resolve after sufficient consistent evidence");
        assertEquals("theoryA", result.leader());
        assertTrue(result.weights().get("theoryA") >= 0.85);
    }

    @Test
    void entropy_decreasesWithConsistentEvidence() {
        var comp = multiObsComposition();
        var tracker = HypothesisTracker.builder(comp)
            .hypothesis("theoryA").priorWeight(0.5)
                .priorOverride("main", "theory", new double[]{0.9, 0.1})
            .hypothesis("theoryB").priorWeight(0.5)
                .priorOverride("main", "theory", new double[]{0.1, 0.9})
            .eliminationThreshold(0.001)
            .build();

        double previousEntropy = tracker.snapshot().entropy();
        String[] obsVars = {"obs1", "obs2", "obs3"};
        for (String obs : obsVars) {
            ObservationResult result = tracker.observeHard("main", obs, "yes");
            assertTrue(result.entropy() <= previousEntropy + 1e-9,
                "Entropy should not increase with consistent evidence: " +
                result.entropy() + " > " + previousEntropy);
            previousEntropy = result.entropy();
        }
        assertTrue(previousEntropy < 0.5, "Entropy should decrease significantly");
    }

    @Test
    void reset_restoresAfterObservations() {
        var comp = twoSlotComposition();
        var tracker = HypothesisTracker.builder(comp)
            .hypothesis("rush").priorWeight(0.5)
                .priorOverride("scouting", "enemy_strategy", new double[]{0.8, 0.1, 0.1})
            .hypothesis("expand").priorWeight(0.5)
                .priorOverride("scouting", "enemy_strategy", new double[]{0.1, 0.8, 0.1})
            .build();

        tracker.observeHard("scouting", "observed_units", "gateway");
        assertNotEquals(0.5, tracker.weights().get("rush"), 1e-9);

        tracker.reset();

        assertEquals(0.5, tracker.weights().get("rush"), 1e-9);
        assertEquals(0.5, tracker.weights().get("expand"), 1e-9);
        assertEquals(2, tracker.viable().size());
        assertEquals(0, tracker.snapshot().observationCount());
    }

    @Test
    void sc2_scoutingScenario_threeHypotheses() {
        var comp = twoSlotComposition();
        var tracker = HypothesisTracker.builder(comp)
            .hypothesis("rush").priorWeight(1.0 / 3)
                .priorOverride("scouting", "enemy_strategy", new double[]{0.8, 0.1, 0.1})
            .hypothesis("expand").priorWeight(1.0 / 3)
                .priorOverride("scouting", "enemy_strategy", new double[]{0.1, 0.8, 0.1})
            .hypothesis("tech").priorWeight(1.0 / 3)
                .priorOverride("scouting", "enemy_strategy", new double[]{0.1, 0.1, 0.8})
            .eliminationThreshold(0.01)
            .build();

        ObservationResult r1 = tracker.observeHard("scouting", "observed_units", "gateway");
        assertEquals(3, r1.viable().size());
        assertEquals("rush", r1.leader());

        TrackerSnapshot snap = tracker.snapshot();
        assertEquals(1, snap.observationCount());
        assertTrue(snap.weights().get("rush") > snap.weights().get("expand"),
            "Rush should dominate after gateway observation");
        assertTrue(snap.weights().get("rush") > snap.weights().get("tech"),
            "Rush should dominate over tech after gateway observation");
    }

    @Test
    void softEvidence_distributionObservation() {
        var comp = twoSlotComposition();
        var tracker = HypothesisTracker.builder(comp)
            .hypothesis("rush").priorWeight(0.5)
                .priorOverride("scouting", "enemy_strategy", new double[]{0.8, 0.1, 0.1})
            .hypothesis("expand").priorWeight(0.5)
                .priorOverride("scouting", "enemy_strategy", new double[]{0.1, 0.8, 0.1})
            .build();

        ObservationResult result = tracker.observe("scouting", "observed_units",
            new double[]{0.7, 0.2, 0.1});

        assertTrue(result.weights().get("rush") > result.weights().get("expand"),
            "Rush should be favored by gateway-heavy soft evidence");
        assertEquals(2, result.viable().size());
    }

    @Test
    void priorOverrides_differentiateHypotheses() {
        JunctionTree tree = BayesNetworkBuilder.create("simple")
            .variable("X", "a", "b")
                .prior(0.5, 0.5)
            .variable("Y", "yes", "no")
                .given("X")
                .cpt(0.9, 0.1,
                     0.1, 0.9)
            .buildJunctionTree();

        var comp = CompositionBuilder.create("override-test")
            .slot("main", tree)
            .build();

        var tracker = HypothesisTracker.builder(comp)
            .hypothesis("favorsA").priorWeight(0.5)
                .priorOverride("main", "X", new double[]{0.9, 0.1})
            .hypothesis("favorsB").priorWeight(0.5)
                .priorOverride("main", "X", new double[]{0.1, 0.9})
            .eliminationThreshold(0.001)
            .build();

        ObservationResult result = tracker.observeHard("main", "Y", "yes");
        assertTrue(result.weights().get("favorsA") > result.weights().get("favorsB"),
            "favorsA should dominate after Y=yes observation");
    }


    @Test
    void setPerTickEvidence_setsEvidenceWithoutChangingWeights() {
        var comp = twoSlotComposition();
        var tracker = HypothesisTracker.builder(comp)
                                       .hypothesis("rush").priorWeight(0.5)
                                       .priorOverride("scouting", "enemy_strategy", new double[]{0.8, 0.1, 0.1})
                                       .hypothesis("expand").priorWeight(0.5)
                                       .priorOverride("scouting", "enemy_strategy", new double[]{0.1, 0.8, 0.1})
                                       .build();

        Map<String, Double> before = tracker.weights();

        tracker.setPerTickEvidence("scouting", "observed_units", new double[]{0.7, 0.2, 0.1});
        tracker.setPerTickEvidence("scouting", "observed_units", new double[]{0.7, 0.2, 0.1});
        tracker.setPerTickEvidence("scouting", "observed_units", new double[]{0.7, 0.2, 0.1});

        Map<String, Double> after = tracker.weights();
        assertEquals(before.get("rush"), after.get("rush"), 1e-9,
                     "Weights must not change from setPerTickEvidence");
        assertEquals(before.get("expand"), after.get("expand"), 1e-9,
                     "Weights must not change from setPerTickEvidence");
    }

    @Test
    void clearPerTickEvidence_removesEvidenceWithoutChangingWeights() {
        var comp = twoSlotComposition();
        var tracker = HypothesisTracker.builder(comp)
                                       .hypothesis("rush").priorWeight(0.5)
                                       .priorOverride("scouting", "enemy_strategy", new double[]{0.8, 0.1, 0.1})
                                       .hypothesis("expand").priorWeight(0.5)
                                       .priorOverride("scouting", "enemy_strategy", new double[]{0.1, 0.8, 0.1})
                                       .build();

        tracker.setPerTickEvidence("scouting", "observed_units", new double[]{0.7, 0.2, 0.1});
        Map<String, Double> before = tracker.weights();

        tracker.clearPerTickEvidence("scouting", "observed_units");

        Map<String, Double> after = tracker.weights();
        assertEquals(before.get("rush"), after.get("rush"), 1e-9);
        assertEquals(before.get("expand"), after.get("expand"), 1e-9);
    }

    @Test
    void marginalize_returnsPerHypothesisMarginals() {
        var comp = twoSlotComposition();
        var tracker = HypothesisTracker.builder(comp)
                                       .hypothesis("rush").priorWeight(0.5)
                                       .priorOverride("scouting", "enemy_strategy", new double[]{0.8, 0.1, 0.1})
                                       .hypothesis("expand").priorWeight(0.5)
                                       .priorOverride("scouting", "enemy_strategy", new double[]{0.1, 0.8, 0.1})
                                       .build();

        double[] rushMarginal   = tracker.marginalize("rush", "scouting", "enemy_strategy");
        double[] expandMarginal = tracker.marginalize("expand", "scouting", "enemy_strategy");

        assertNotNull(rushMarginal);
        assertNotNull(expandMarginal);
        assertEquals(3, rushMarginal.length);
        assertEquals(3, expandMarginal.length);
        assertTrue(rushMarginal[0] > expandMarginal[0],
                   "Rush hypothesis should have higher P(rush) marginal");
        assertTrue(expandMarginal[1] > rushMarginal[1],
                   "Expand hypothesis should have higher P(expand) marginal");
    }

    @Test
    void marginalize_rejectsUnknownHypothesis() {
        var comp = twoSlotComposition();
        var tracker = HypothesisTracker.builder(comp)
                                       .hypothesis("rush").priorWeight(1.0)
                                       .build();

        assertThrows(IllegalArgumentException.class,
                     () -> tracker.marginalize("nonexistent", "scouting", "enemy_strategy"));
    }
}

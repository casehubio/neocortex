package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionInstanceTest {

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

    @Test
    void twoNetworkChain_posteriorFlows() {
        var comp = CompositionBuilder.create("sc2")
            .slot("scouting", scoutingTree())
            .slot("buildOrder", buildOrderTree())
            .link("scouting", "enemy_strategy").to("buildOrder", "enemy_strategy")
            .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("scouting", "observed_units", "gateway");
        CompositionStats stats = instance.update();

        double[] buildChoice = instance.marginalize("buildOrder", "build_choice");
        assertEquals(3, buildChoice.length);
        assertTrue(buildChoice[0] > buildChoice[1],
            "Defensive should dominate when gateway (rush) observed");
        assertEquals(2, stats.slotsUpdated());
        assertEquals(0, stats.slotsSkipped());
    }

    @Test
    void linkTargetProtection_throwsOnSetEvidence() {
        var comp = CompositionBuilder.create("test")
            .slot("A", scoutingTree())
            .slot("B", buildOrderTree())
            .link("A", "enemy_strategy").to("B", "enemy_strategy")
            .build();

        var instance = new CompositionInstance(comp);

        assertThrows(IllegalArgumentException.class, () ->
            instance.setEvidence("B", "enemy_strategy", new double[]{0.5, 0.3, 0.2}));
    }

    @Test
    void linkTargetProtection_throwsOnSetHardEvidence() {
        var comp = CompositionBuilder.create("test")
            .slot("A", scoutingTree())
            .slot("B", buildOrderTree())
            .link("A", "enemy_strategy").to("B", "enemy_strategy")
            .build();

        var instance = new CompositionInstance(comp);

        assertThrows(IllegalArgumentException.class, () ->
            instance.setHardEvidence("B", "enemy_strategy", "rush"));
    }

    @Test
    void skipIfUnchanged_skipsDownstream() {
        var comp = CompositionBuilder.create("test")
            .slot("A", scoutingTree())
            .slot("B", buildOrderTree())
            .link("A", "enemy_strategy").to("B", "enemy_strategy")
            .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("A", "observed_units", "gateway");
        instance.update();

        CompositionStats stats = instance.update();
        assertEquals(0, stats.slotsUpdated());
        assertEquals(2, stats.slotsSkipped());
    }

    @Test
    void mapTransfer_injectsHardEvidence() {
        var comp = CompositionBuilder.create("test")
            .slot("A", scoutingTree())
            .slot("B", buildOrderTree())
            .link("A", "enemy_strategy").to("B", "enemy_strategy")
                .mode(TransferMode.MAP)
            .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("A", "observed_units", "gateway");
        instance.update();

        double[] enemyInB = instance.marginalize("B", "enemy_strategy");
        double max = 0;
        for (double v : enemyInB) { if (v > max) max = v; }
        assertEquals(1.0, max, 1e-10, "MAP transfer should produce hard evidence");
    }

    @Test
    void diamondComposition_batchesUpstream() {
        JunctionTree treeABC = BayesNetworkBuilder.create()
                                                  .variable("X", "low", "high").prior(0.5, 0.5)
                                                  .variable("Y", "low", "high").given("X").cpt(0.8, 0.2, 0.3, 0.7)
                                                  .buildJunctionTree();

        JunctionTree treeD = BayesNetworkBuilder.create()
                                                .variable("fromB", "low", "high").prior(0.5, 0.5)
                                                .variable("fromC", "low", "high").prior(0.5, 0.5)
                                                .variable("result", "low", "high")
                                                .given("fromB", "fromC")
                                                .cpt(0.9, 0.1,
                                                     0.6, 0.4,
                                                     0.5, 0.5,
                                                     0.2, 0.8)
                                                .buildJunctionTree();

        var comp = CompositionBuilder.create("diamond")
                                     .slot("A", treeABC)
                                     .slot("B", treeABC)
                                     .slot("C", treeABC)
                                     .slot("D", treeD)
                                     .link("A", "Y").to("B", "X")
                                     .link("A", "Y").to("C", "X")
                                     .link("B", "Y").to("D", "fromB")
                                     .link("C", "Y").to("D", "fromC")
                                     .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("A", "X", "high");
        CompositionStats stats = instance.update();

        double[] result = instance.marginalize("D", "result");
        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(4, stats.slotsUpdated());
        double sum = 0;
        for (double v : result) {sum += v;}
        assertEquals(1.0, sum, 1e-6, "Result distribution should sum to 1.0");

        // Verify D's result differs from its unconditioned prior
        double[] priorResult = new double[]{0.55, 0.45};
        boolean  differs     = false;
        for (int i = 0; i < result.length; i++) {
            if (Math.abs(result[i] - priorResult[i]) > 0.01) {differs = true;}
        }
        assertTrue(differs, "D's result should differ from unconditioned prior after upstream evidence");
    }

    @Test
    void marginalize_returnsDefensiveCopy() {
        var comp = CompositionBuilder.create("test")
            .slot("A", scoutingTree())
            .build();

        var instance = new CompositionInstance(comp);
        instance.update();

        double[] dist1 = instance.marginalize("A", "enemy_strategy");
        double[] dist2 = instance.marginalize("A", "enemy_strategy");
        assertNotSame(dist1, dist2);
        assertArrayEquals(dist1, dist2, 1e-10);
    }

    @Test
    void reset_clearsAllEvidence() {
        var comp = CompositionBuilder.create("test")
            .slot("A", scoutingTree())
            .slot("B", buildOrderTree())
            .link("A", "enemy_strategy").to("B", "enemy_strategy")
            .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("A", "observed_units", "gateway");
        instance.update();

        instance.reset();
        instance.update();

        double[] after = instance.marginalize("A", "enemy_strategy");
        for (int i = 0; i < after.length; i++) {
            assertEquals(1.0 / 3, after[i], 0.01,
                "After reset, should approximate prior");
        }
    }

    @Test
    void unknownSlot_throwsOnSetEvidence() {
        var comp = CompositionBuilder.create("test")
            .slot("A", scoutingTree())
            .build();

        var instance = new CompositionInstance(comp);

        assertThrows(IllegalArgumentException.class, () ->
            instance.setEvidence("Z", "X", new double[]{0.5, 0.5, 0.0}));
    }

    @Test
    void unknownSlot_throwsOnMarginalize() {
        var comp = CompositionBuilder.create("test")
            .slot("A", scoutingTree())
            .build();

        var instance = new CompositionInstance(comp);

        assertThrows(IllegalArgumentException.class, () ->
            instance.marginalize("Z", "X"));
    }

    @Test
    void getStats_returnsSlotStats() {
        var comp = CompositionBuilder.create("test")
            .slot("A", scoutingTree())
            .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("A", "observed_units", "gateway");
        instance.update();

        BayesStats stats = instance.getStats("A");
        assertNotNull(stats);
        assertEquals(1, stats.evidenceVariableCount());
    }

    @Test
    void unsetEvidence_removesEvidence() {
        var comp = CompositionBuilder.create("test")
                                     .slot("A", scoutingTree())
                                     .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("A", "observed_units", "gateway");
        instance.update();
        double[] withEvidence = instance.marginalize("A", "enemy_strategy");

        instance.unsetEvidence("A", "observed_units");
        instance.update();
        double[] afterUnset = instance.marginalize("A", "enemy_strategy");

        for (int i = 0; i < afterUnset.length; i++) {
            assertEquals(1.0 / 3, afterUnset[i], 0.01, "Should return to prior after unset");
        }
    }

    @Test
    void linkTargetProtection_throwsOnUnsetEvidence() {
        var comp = CompositionBuilder.create("test")
                                     .slot("A", scoutingTree())
                                     .slot("B", buildOrderTree())
                                     .link("A", "enemy_strategy").to("B", "enemy_strategy")
                                     .build();

        var instance = new CompositionInstance(comp);

        assertThrows(IllegalArgumentException.class, () ->
                                                             instance.unsetEvidence("B", "enemy_strategy"));
    }

    @Test
    void getStats_unknownSlot_throws() {
        var comp = CompositionBuilder.create("test")
                                     .slot("A", scoutingTree())
                                     .build();

        var instance = new CompositionInstance(comp);

        assertThrows(IllegalArgumentException.class, () ->
                                                             instance.getStats("Z"));
    }

    @Test
    void mixedPosteriorAndMapLinks() {
        JunctionTree treeA = scoutingTree();
        JunctionTree treeB = BayesNetworkBuilder.create("B")
                                                .variable("enemy_strategy", "rush", "expand", "tech").prior(1.0 / 3, 1.0 / 3, 1.0 / 3)
                                                .variable("result", "low", "high")
                                                .given("enemy_strategy")
                                                .cpt(0.8, 0.2, 0.3, 0.7, 0.5, 0.5)
                                                .buildJunctionTree();
        JunctionTree treeC = BayesNetworkBuilder.create("C")
                                                .variable("enemy_strategy", "rush", "expand", "tech").prior(1.0 / 3, 1.0 / 3, 1.0 / 3)
                                                .variable("assessment", "bad", "good")
                                                .given("enemy_strategy")
                                                .cpt(0.9, 0.1, 0.2, 0.8, 0.5, 0.5)
                                                .buildJunctionTree();

        var comp = CompositionBuilder.create("mixed")
                                     .slot("A", treeA)
                                     .slot("B", treeB)
                                     .slot("C", treeC)
                                     .link("A", "enemy_strategy").to("B", "enemy_strategy")
                                     .link("A", "enemy_strategy").to("C", "enemy_strategy").mode(TransferMode.MAP)
                                     .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("A", "observed_units", "gateway");
        CompositionStats stats = instance.update();

        double[] bResult   = instance.marginalize("B", "result");
        double[] cStrategy = instance.marginalize("C", "enemy_strategy");

        assertNotNull(bResult);
        assertEquals(3, stats.slotsUpdated());

        // B got POSTERIOR — soft evidence, not 1.0
        double bMax = 0;
        for (double v : bResult) {if (v > bMax) {bMax = v;}}
        assertTrue(bMax < 1.0, "POSTERIOR should produce soft evidence");

        // C got MAP — hard evidence, one state is 1.0
        double cMax = 0;
        for (double v : cStrategy) {if (v > cMax) {cMax = v;}}
        assertEquals(1.0, cMax, 1e-10, "MAP should produce hard evidence");
    }

    @Test
    void skipIfUnchanged_preservesMarginals() {
        var comp = CompositionBuilder.create("test")
                                     .slot("A", scoutingTree())
                                     .slot("B", buildOrderTree())
                                     .link("A", "enemy_strategy").to("B", "enemy_strategy")
                                     .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("A", "observed_units", "gateway");
        instance.update();
        double[] before = instance.marginalize("B", "build_choice");

        instance.update();
        double[] after = instance.marginalize("B", "build_choice");

        assertArrayEquals(before, after, 1e-10, "Marginals should be unchanged when skipped");
    }

    @Test
    void singleSlotNoLinks_updatesNormally() {
        var comp = CompositionBuilder.create("solo")
                                     .slot("A", scoutingTree())
                                     .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("A", "observed_units", "gateway");
        CompositionStats stats = instance.update();

        double[] result = instance.marginalize("A", "enemy_strategy");
        assertEquals(1, stats.slotsUpdated());
        assertTrue(result[0] > result[1], "Rush should dominate when gateway observed");
    }


    @Test
    void sensitivity_delegatesToSlotInstance() {
        var comp = CompositionBuilder.create("sc2")
                                     .slot("scouting", scoutingTree())
                                     .slot("buildOrder", buildOrderTree())
                                     .link("scouting", "enemy_strategy").to("buildOrder", "enemy_strategy")
                                     .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("scouting", "observed_units", "gateway");
        instance.update();

        double sens = instance.sensitivity("scouting", "observed_units", "enemy_strategy");
        assertTrue(sens > 0, "Sensitivity should be positive when evidence is informative");
    }
}

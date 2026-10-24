package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        JunctionTree tree = BayesNetworkBuilder.create()
            .variable("X", "low", "high").prior(0.5, 0.5)
            .variable("Y", "low", "high").given("X").cpt(0.8, 0.2, 0.3, 0.7)
            .buildJunctionTree();

        var comp = CompositionBuilder.create("diamond")
            .slot("A", tree)
            .slot("B", tree)
            .slot("C", tree)
            .slot("D", tree)
            .link("A", "Y").to("B", "X")
            .link("A", "Y").to("C", "X")
            .link("B", "Y").to("D", "X")
            .link("C", "Y").to("D", "X")
            .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("A", "X", "high");
        CompositionStats stats = instance.update();

        double[] dY = instance.marginalize("D", "Y");
        assertNotNull(dY);
        assertEquals(2, dY.length);
        assertEquals(4, stats.slotsUpdated());
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
}

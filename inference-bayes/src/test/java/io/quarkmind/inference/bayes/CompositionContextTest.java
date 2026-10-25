package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionContextTest {

    private JunctionTree scoutingTvP() {
        return BayesNetworkBuilder.create("scouting-TvP")
            .variable("observed_units", "gateway", "forge", "nexus")
                .prior(1.0 / 3, 1.0 / 3, 1.0 / 3)
            .variable("enemy_strategy", "rush", "expand", "tech")
                .given("observed_units")
                .cpt(0.7, 0.1, 0.2,
                     0.1, 0.2, 0.7,
                     0.1, 0.7, 0.2)
            .buildJunctionTree();
    }

    private JunctionTree scoutingTvZ() {
        return BayesNetworkBuilder.create("scouting-TvZ")
            .variable("observed_units", "spawning_pool", "hatchery", "lair")
                .prior(1.0 / 3, 1.0 / 3, 1.0 / 3)
            .variable("enemy_strategy", "rush", "expand", "tech")
                .given("observed_units")
                .cpt(0.8, 0.1, 0.1,
                     0.1, 0.8, 0.1,
                     0.1, 0.1, 0.8)
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
    void activateVariant_changesDownstreamResults() {
        var comp = CompositionBuilder.create("sc2")
            .slot("scouting", scoutingTvP())
                .variant("TvP", scoutingTvP())
                .variant("TvZ", scoutingTvZ())
            .slot("buildOrder", buildOrderTree())
            .link("scouting", "enemy_strategy").to("buildOrder", "enemy_strategy")
            .build();

        var instance = new CompositionInstance(comp);

        instance.setHardEvidence("scouting", "observed_units", "gateway");
        instance.update();
        double[] buildTvP = instance.marginalize("buildOrder", "build_choice");

        instance.activate("scouting", "TvZ");
        instance.setHardEvidence("scouting", "observed_units", "spawning_pool");
        instance.update();
        double[] buildTvZ = instance.marginalize("buildOrder", "build_choice");

        assertTrue(buildTvP[0] > 0.5, "TvP: defensive should dominate");
        assertTrue(buildTvZ[0] > 0.5, "TvZ: defensive should dominate");
        assertFalse(Arrays.equals(buildTvP, buildTvZ),
            "Different scouting models should produce different build distributions");
    }

    @Test
    void activateVariant_noEvidenceCarryOver() {
        var comp = CompositionBuilder.create("test")
            .slot("scouting", scoutingTvP())
                .variant("TvP", scoutingTvP())
                .variant("TvZ", scoutingTvZ())
            .slot("buildOrder", buildOrderTree())
            .link("scouting", "enemy_strategy").to("buildOrder", "enemy_strategy")
            .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("scouting", "observed_units", "gateway");
        instance.update();

        instance.activate("scouting", "TvZ");
        instance.update();

        double[] strategy = instance.marginalize("scouting", "enemy_strategy");
        for (double v : strategy) {
            assertEquals(1.0 / 3, v, 0.05, "Should be near-uniform without evidence");
        }
    }

    @Test
    void activateVariant_upstreamUnaffected() {
        JunctionTree economyTree = BayesNetworkBuilder.create("economy")
            .variable("worker_count", "behind", "even", "ahead")
                .prior(0.3, 0.4, 0.3)
            .buildJunctionTree();

        var comp = CompositionBuilder.create("test")
            .slot("economy", economyTree)
            .slot("scouting", scoutingTvP())
                .variant("TvP", scoutingTvP())
                .variant("TvZ", scoutingTvZ())
            .slot("buildOrder", buildOrderTree())
            .link("scouting", "enemy_strategy").to("buildOrder", "enemy_strategy")
            .build();

        var instance = new CompositionInstance(comp);
        instance.update();

        double[] economyBefore = instance.marginalize("economy", "worker_count");
        instance.activate("scouting", "TvZ");
        instance.update();
        double[] economyAfter = instance.marginalize("economy", "worker_count");

        assertArrayEquals(economyBefore, economyAfter, 1e-10,
            "Upstream slot should be unaffected by downstream activation");
    }

    @Test
    void activateUnknownVariant_throws() {
        var comp = CompositionBuilder.create("test")
            .slot("scouting", scoutingTvP())
                .variant("TvP", scoutingTvP())
            .build();

        var instance = new CompositionInstance(comp);

        assertThrows(IllegalArgumentException.class, () ->
            instance.activate("scouting", "TvZ"));
    }

    @Test
    void activateUnknownSlot_throws() {
        var comp = CompositionBuilder.create("test")
            .slot("scouting", scoutingTvP())
            .build();

        var instance = new CompositionInstance(comp);

        assertThrows(IllegalArgumentException.class, () ->
            instance.activate("unknown", "TvP"));
    }

    @Test
    void sc2Scenario_fullComposition() {
        var comp = CompositionBuilder.create("sc2-decision")
            .slot("scouting", scoutingTvP())
                .variant("TvP", scoutingTvP())
                .variant("TvZ", scoutingTvZ())
            .slot("buildOrder", buildOrderTree())
            .link("scouting", "enemy_strategy").to("buildOrder", "enemy_strategy")
            .build();

        var instance = new CompositionInstance(comp);

        // TvP game: observe gateway → rush likely → defensive build
        instance.setHardEvidence("scouting", "observed_units", "gateway");
        instance.update();
        double[] buildTvP = instance.marginalize("buildOrder", "build_choice");
        assertTrue(buildTvP[0] > buildTvP[1] && buildTvP[0] > buildTvP[2],
            "Defensive should be highest when rush observed");

        // Switch to TvZ: observe hatchery → expand likely → greedy build
        instance.activate("scouting", "TvZ");
        instance.setHardEvidence("scouting", "observed_units", "hatchery");
        instance.update();
        double[] buildTvZ = instance.marginalize("buildOrder", "build_choice");
        assertTrue(buildTvZ[1] > buildTvZ[0],
            "Greedy should dominate when expand observed");
    }

    @Test
    void multipleSequentialActivations() {
        var comp = CompositionBuilder.create("test")
                                     .slot("scouting", scoutingTvP())
                                     .variant("TvP", scoutingTvP())
                                     .variant("TvZ", scoutingTvZ())
                                     .slot("buildOrder", buildOrderTree())
                                     .link("scouting", "enemy_strategy").to("buildOrder", "enemy_strategy")
                                     .build();

        var instance = new CompositionInstance(comp);

        instance.activate("scouting", "TvZ");
        instance.setHardEvidence("scouting", "observed_units", "spawning_pool");
        instance.update();
        double[] afterTvZ = instance.marginalize("buildOrder", "build_choice");

        instance.activate("scouting", "TvP");
        instance.setHardEvidence("scouting", "observed_units", "gateway");
        instance.update();
        double[] afterTvP = instance.marginalize("buildOrder", "build_choice");

        assertFalse(Arrays.equals(afterTvZ, afterTvP),
                    "Different variants should produce different results");
    }

    @Test
    void activateDefault_revertsToDefaultTree() {
        var comp = CompositionBuilder.create("test")
                                     .slot("scouting", scoutingTvP())
                                     .variant("TvP", scoutingTvP())
                                     .variant("TvZ", scoutingTvZ())
                                     .slot("buildOrder", buildOrderTree())
                                     .link("scouting", "enemy_strategy").to("buildOrder", "enemy_strategy")
                                     .build();

        var instance = new CompositionInstance(comp);
        instance.setHardEvidence("scouting", "observed_units", "gateway");
        instance.update();
        double[] withDefault = instance.marginalize("buildOrder", "build_choice");

        instance.activate("scouting", "TvZ");
        instance.setHardEvidence("scouting", "observed_units", "spawning_pool");
        instance.update();

        instance.activateDefault("scouting");
        instance.setHardEvidence("scouting", "observed_units", "gateway");
        instance.update();
        double[] afterRevert = instance.marginalize("buildOrder", "build_choice");

        assertArrayEquals(withDefault, afterRevert, 1e-10,
                          "Reverting to default should produce same results as initial default");
    }

    @Test
    void activateThenReset_variantPersistsEvidenceClears() {
        var comp = CompositionBuilder.create("test")
                                     .slot("scouting", scoutingTvP())
                                     .variant("TvZ", scoutingTvZ())
                                     .slot("buildOrder", buildOrderTree())
                                     .link("scouting", "enemy_strategy").to("buildOrder", "enemy_strategy")
                                     .build();

        var instance = new CompositionInstance(comp);
        instance.activate("scouting", "TvZ");
        instance.setHardEvidence("scouting", "observed_units", "spawning_pool");
        instance.update();

        instance.reset();
        instance.update();

        double[] strategy = instance.marginalize("scouting", "enemy_strategy");
        for (double v : strategy) {
            assertEquals(1.0 / 3, v, 0.05, "Evidence should be cleared but variant persists");
        }
    }

}

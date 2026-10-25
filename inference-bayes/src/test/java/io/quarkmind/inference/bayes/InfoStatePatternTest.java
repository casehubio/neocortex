package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InfoStatePatternTest {

    private JunctionTree infoStateNetwork() {
        return BayesNetworkBuilder.create("sc2-info-state")
            .variable("observed_units", "gateway", "hatchery", "barracks")
                .prior(1.0 / 3, 1.0 / 3, 1.0 / 3)
            .variable("enemy_strategy", "rush", "expand", "tech")
                .given("observed_units")
                .cpt(0.7, 0.1, 0.2,
                     0.1, 0.7, 0.2,
                     0.2, 0.2, 0.6)
            .variable("opponent_saw_units", "true", "false")
                .given("observed_units")
                .cpt(0.3, 0.7,
                     0.3, 0.7,
                     0.3, 0.7)
            .variable("opponent_belief", "rush", "expand", "tech")
                .given("enemy_strategy", "opponent_saw_units")
                .cpt(
                     0.7, 0.1, 0.2,         // strategy=rush, saw=true
                     1.0/3, 1.0/3, 1.0/3,   // strategy=rush, saw=false
                     0.1, 0.7, 0.2,          // strategy=expand, saw=true
                     1.0/3, 1.0/3, 1.0/3,    // strategy=expand, saw=false
                     0.2, 0.2, 0.6,          // strategy=tech, saw=true
                     1.0/3, 1.0/3, 1.0/3     // strategy=tech, saw=false
                )
            .buildJunctionTree();
    }

    @Test
    void scoutingEvidence_shiftsOpponentBeliefs() {
        var instance = new BayesInstance<>(infoStateNetwork());

        instance.setHardEvidence("observed_units", "gateway");
        instance.globalUpdate();

        double[] opponentBelief = instance.marginalize("opponent_belief").getDistribution();

        assertTrue(opponentBelief[0] > opponentBelief[1],
            "Opponent should believe rush is more likely than expand when gateways observed");
    }

    @Test
    void noScoutingEvidence_opponentBeliefCloserToUniform() {
        var instance = new BayesInstance<>(infoStateNetwork());

        instance.setHardEvidence("opponent_saw_units", "false");
        instance.globalUpdate();

        double[] opponentBelief = instance.marginalize("opponent_belief").getDistribution();

        double maxDiff = 0;
        for (int i = 0; i < opponentBelief.length; i++) {
            maxDiff = Math.max(maxDiff, Math.abs(opponentBelief[i] - 1.0 / 3));
        }
        assertTrue(maxDiff < 0.05,
            "Without scouting, opponent beliefs should be near-uniform");
    }

    @Test
    void confirmedScouting_strongerBeliefShift() {
        var instance1 = new BayesInstance<>(infoStateNetwork());
        instance1.setHardEvidence("observed_units", "gateway");
        instance1.globalUpdate();
        double[] withoutScoutConfirm = instance1.marginalize("opponent_belief").getDistribution();

        var instance2 = new BayesInstance<>(infoStateNetwork());
        instance2.setHardEvidence("observed_units", "gateway");
        instance2.setHardEvidence("opponent_saw_units", "true");
        instance2.globalUpdate();
        double[] withScoutConfirm = instance2.marginalize("opponent_belief").getDistribution();

        assertTrue(withScoutConfirm[0] > withoutScoutConfirm[0],
            "Confirmed scouting should strengthen opponent's rush belief");
    }
}

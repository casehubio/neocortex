package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.BehavioralAttractor;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.SettlingResult;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DispositionAttractorDifferentiationTest {

    private static CapsTopology topology;

    @BeforeAll
    static void loadTopology() {
        topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
    }

    @Test
    void competitiveProducesStrongerApproachThanCooperative() {
        var competitive = settle("competitive", "bold", "flexible", "independent", "confrontational");
        var cooperative = settle("cooperative", "conservative", "strict", "dependent", "avoidant");

        double compApproach = strengthOf(competitive, "approach");
        double coopApproach = strengthOf(cooperative, "approach");

        assertThat(compApproach).as("competitive approach").isGreaterThan(coopApproach);
        assertThat(compApproach).as("competitive approach tier").isGreaterThan(0.7);
    }

    @Test
    void cooperativeProducesStrongerCautiousApproachThanCompetitive() {
        var competitive = settle("competitive", "bold", "flexible", "independent", "confrontational");
        var cooperative = settle("cooperative", "conservative", "strict", "dependent", "avoidant");

        double coopCautious = strengthOf(cooperative, "cautious_approach");
        double compCautious = strengthOf(competitive, "cautious_approach");

        assertThat(coopCautious).as("cooperative cautious_approach").isGreaterThan(compCautious);
        assertThat(coopCautious).as("cooperative cautious tier").isGreaterThan(0.7);
    }

    @Test
    void sameInputProducesDifferentiatedProfiles() {
        var competitive = settle("competitive", "bold", "flexible", "independent", "confrontational");
        var cooperative = settle("cooperative", "conservative", "strict", "dependent", "avoidant");

        var compTop = competitive.attractors().stream()
                .max((a, b) -> Double.compare(a.strength(), b.strength()))
                .map(BehavioralAttractor::nodeId).orElse("");
        var coopTop = cooperative.attractors().stream()
                .max((a, b) -> Double.compare(a.strength(), b.strength()))
                .map(BehavioralAttractor::nodeId).orElse("");

        assertThat(compTop).as("competitive dominant attractor")
                .isNotEqualTo(coopTop);
    }

    private SettlingResult settle(String socialOrient, String riskAppetite,
                                  String ruleFollowing, String autonomy,
                                  String conflictMode) {
        var disposition = new DispositionAxes(socialOrient, ruleFollowing,
                riskAppetite, autonomy, conflictMode);
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "agent-1", disposition);

        var settler = new CapsSettler(topology);
        return settler.settle(state, Map.of(
                "secure_attachment", 0.5,
                "acceptance", 0.4,
                "mastery", 0.3));
    }

    private double strengthOf(SettlingResult result, String nodeId) {
        return result.attractors().stream()
                .filter(a -> nodeId.equals(a.nodeId()))
                .mapToDouble(BehavioralAttractor::strength)
                .findFirst().orElse(0.0);
    }
}

package io.casehub.neocortex.cognition.tom;

import io.casehub.neocortex.cognition.appraisal.ActionReadiness;
import io.casehub.neocortex.cognition.appraisal.ActionTendency;
import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.cognition.mentalmodel.TheoryOfMindPosterior;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TheoryOfMindBridgeTest {

    private TheoryOfMindBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new TheoryOfMindBridge();
    }

    @Test
    void highThreatAvoidantProducesDistress() {
        var appraisal = appraisalWith(
                Map.of("conduciveness", -0.8, "controllability", 0.1),
                List.of(new ActionTendency(ActionReadiness.AVOIDANCE, 0.9, "")));

        var posterior = bridge.infer(appraisal);
        assertEquals(1.0, posterior.positive() + posterior.neutral() + posterior.distressed(), 0.01);
        assertTrue(posterior.distressed() > 0.6,
                "high threat + avoidant should produce high distress, was " + posterior.distressed());
        assertEquals("DISTRESSED", posterior.dominantValence());
    }

    @Test
    void lowThreatApproachProducesPositive() {
        var appraisal = appraisalWith(
                Map.of("conduciveness", 0.7, "controllability", 0.9),
                List.of(new ActionTendency(ActionReadiness.APPROACH, 0.8, "")));

        var posterior = bridge.infer(appraisal);
        assertTrue(posterior.positive() > posterior.distressed(),
                "low threat + approach should favour positive over distressed");
        assertEquals("POSITIVE", posterior.dominantValence());
    }

    @Test
    void neutralAppraisalProducesNeutralDominant() {
        var appraisal = appraisalWith(
                Map.of("conduciveness", 0.0, "controllability", 0.5),
                List.of(new ActionTendency(ActionReadiness.ATTENDING, 0.5, "")));

        var posterior = bridge.infer(appraisal);
        assertEquals(1.0, posterior.positive() + posterior.neutral() + posterior.distressed(), 0.01);
        assertTrue(posterior.neutral() > 0.3,
                "neutral appraisal should have substantial neutral probability");
    }

    @Test
    void emptyTendenciesProduceValidPosterior() {
        var appraisal = appraisalWith(
                Map.of("conduciveness", -0.5, "controllability", 0.3),
                List.of());

        var posterior = bridge.infer(appraisal);
        assertEquals(1.0, posterior.positive() + posterior.neutral() + posterior.distressed(), 0.01);
    }

    @Test
    void emptySecDimensionsUseDefaults() {
        var appraisal = appraisalWith(
                Map.of(),
                List.of(new ActionTendency(ActionReadiness.APPROACH, 0.7, "")));

        var posterior = bridge.infer(appraisal);
        assertEquals(1.0, posterior.positive() + posterior.neutral() + posterior.distressed(), 0.01);
    }

    @Test
    void threatDistributionSumsToOne() {
        double[] dist = TheoryOfMindBridge.threatDistribution(
                Map.of("conduciveness", -0.5, "controllability", 0.2));
        assertEquals(1.0, dist[0] + dist[1] + dist[2], 0.001);
        assertTrue(dist[2] > dist[0], "severe should exceed none for negative conduciveness + low controllability");
    }

    @Test
    void behaviorDistributionSumsToOne() {
        double[] dist = TheoryOfMindBridge.behaviorDistribution(List.of(
                new ActionTendency(ActionReadiness.APPROACH, 0.6, ""),
                new ActionTendency(ActionReadiness.AVOIDANCE, 0.3, ""),
                new ActionTendency(ActionReadiness.ATTENDING, 0.1, "")));
        assertEquals(1.0, dist[0] + dist[1] + dist[2], 0.001);
        assertTrue(dist[0] > dist[2], "approach weight should exceed avoidant weight");
    }

    @Test
    void consecutiveInferencesAreIndependent() {
        var threatening = appraisalWith(
                Map.of("conduciveness", -0.9, "controllability", 0.1),
                List.of(new ActionTendency(ActionReadiness.AVOIDANCE, 0.9, "")));
        var safe = appraisalWith(
                Map.of("conduciveness", 0.8, "controllability", 0.9),
                List.of(new ActionTendency(ActionReadiness.APPROACH, 0.9, "")));

        var distressed = bridge.infer(threatening);
        var positive = bridge.infer(safe);

        assertTrue(positive.positive() > distressed.positive(),
                "safe appraisal should produce more positive than threatening one");
    }

    private static AppraisalResult appraisalWith(Map<String, Double> secDimensions,
                                                  List<ActionTendency> tendencies) {
        return new AppraisalResult(List.of(), tendencies, HabituationState.empty(), null, secDimensions);
    }
}

package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InfluenceDiagramEngineTest {

    @Test
    void noObservation_bestUnconditionalAction() {
        InfluenceDiagram diagram = BayesNetworkBuilder.create()
            .decision("action", "A", "B")
            .utility("payoff").given("action").utilities(10, 3)
            .buildInfluenceDiagram();

        PolicyResult result = InfluenceDiagramEngine.solve(diagram);

        assertEquals("action", result.decisionVariable());
        assertEquals("A", result.optimalPolicy().get(Map.of()));
        assertEquals(10.0, result.maxExpectedUtility(), 0.01);
        assertEquals(10.0, result.actionUtilities().get("A"), 0.01);
        assertEquals(3.0, result.actionUtilities().get("B"), 0.01);
    }

    @Test
    void medicalTreatment_observeSymptoms_optimalPolicy() {
        InfluenceDiagram diagram = BayesNetworkBuilder.create()
            .variable("disease", "yes", "no").prior(0.3, 0.7)
            .variable("symptoms", "mild", "severe")
                .given("disease")
                .cpt(0.2, 0.8,
                     0.9, 0.1)
            .decision("treatment", "treat", "wait")
            .utility("outcome").given("disease", "treatment").utilities(
                8, 2,
                3, 9)
            .buildInfluenceDiagram();

        PolicyResult result = InfluenceDiagramEngine.solve(diagram, "symptoms");

        assertEquals("treat", result.optimalPolicy().get(Map.of("symptoms", "severe")));
        assertEquals("wait", result.optimalPolicy().get(Map.of("symptoms", "mild")));
        assertEquals(2, result.optimalPolicy().size());
    }

    @Test
    void oilWildcatter_observeTest_optimalPolicy() {
        InfluenceDiagram diagram = BayesNetworkBuilder.create()
            .variable("oil", "dry", "wet", "soaking").prior(0.5, 0.3, 0.2)
            .variable("test", "none", "open", "closed")
                .given("oil")
                .cpt(0.6, 0.3, 0.1,
                     0.3, 0.4, 0.3,
                     0.1, 0.4, 0.5)
            .decision("drill", "drill", "no-drill")
            .utility("profit").given("oil", "drill").utilities(
                -70, 0,
                 50, 0,
                200, 0)
            .buildInfluenceDiagram();

        PolicyResult result = InfluenceDiagramEngine.solve(diagram, "test");

        assertEquals("drill", result.optimalPolicy().get(Map.of("test", "closed")));
        assertEquals("no-drill", result.optimalPolicy().get(Map.of("test", "none")));
        assertEquals(3, result.optimalPolicy().size());
    }

    @Test
    void specificEvidence_bestActionGivenObservation() {
        InfluenceDiagram diagram = BayesNetworkBuilder.create()
            .variable("disease", "yes", "no").prior(0.3, 0.7)
            .variable("symptoms", "mild", "severe")
                .given("disease")
                .cpt(0.2, 0.8,
                     0.9, 0.1)
            .decision("treatment", "treat", "wait")
            .utility("outcome").given("disease", "treatment").utilities(
                8, 2,
                3, 9)
            .buildInfluenceDiagram();

        PolicyResult result = InfluenceDiagramEngine.solve(diagram,
            Map.of("symptoms", "severe"));

        assertEquals(1, result.optimalPolicy().size());
        assertEquals("treat", result.optimalPolicy().get(Map.of("symptoms", "severe")));
    }

    @Test
    void multipleUtilityNodes_additiveAggregation() {
        InfluenceDiagram diagram = BayesNetworkBuilder.create()
            .variable("disease", "yes", "no").prior(0.4, 0.6)
            .decision("treatment", "treat", "wait")
            .utility("health").given("disease", "treatment").utilities(
                9, 1,
                4, 8)
            .utility("cost").given("treatment").utilities(
                -5, 0)
            .buildInfluenceDiagram();

        PolicyResult result = InfluenceDiagramEngine.solve(diagram);

        // treat EU: 0.4*(9-5) + 0.6*(4-5) = 1.6 - 0.6 = 1.0
        // wait EU:  0.4*(1+0) + 0.6*(8+0) = 0.4 + 4.8 = 5.2
        assertEquals("wait", result.optimalPolicy().get(Map.of()));
        assertEquals(5.2, result.maxExpectedUtility(), 0.01);
        assertEquals(1.0, result.actionUtilities().get("treat"), 0.01);
        assertEquals(5.2, result.actionUtilities().get("wait"), 0.01);
    }

    @Test
    void multiChanceParent_chainRuleJointComputation() {
        InfluenceDiagram diagram = BayesNetworkBuilder.create()
            .variable("weather", "sun", "rain").prior(0.6, 0.4)
            .variable("traffic", "light", "heavy").prior(0.5, 0.5)
            .decision("route", "highway", "backroad")
            .utility("time").given("weather", "traffic", "route").utilities(
                10, 8,
                3, 7,
                6, 5,
                1, 4)
            .buildInfluenceDiagram();

        PolicyResult result = InfluenceDiagramEngine.solve(diagram);

        // highway EU = 0.6*0.5*10 + 0.6*0.5*3 + 0.4*0.5*6 + 0.4*0.5*1
        //            = 3.0 + 0.9 + 1.2 + 0.2 = 5.3
        // backroad EU = 0.6*0.5*8 + 0.6*0.5*7 + 0.4*0.5*5 + 0.4*0.5*4
        //             = 2.4 + 2.1 + 1.0 + 0.8 = 6.3
        assertEquals("backroad", result.optimalPolicy().get(Map.of()));
        assertEquals(6.3, result.maxExpectedUtility(), 0.01);
        assertEquals(5.3, result.actionUtilities().get("highway"), 0.01);
        assertEquals(6.3, result.actionUtilities().get("backroad"), 0.01);
    }

    @Test
    void multipleUtilityNodes_decisionOnlyFirstThenChance() {
        // Regression: utility with only-decision parents declared BEFORE
        // utility with chance parents. Previously crashed due to early return
        // in ensureUtilityParentConnectivity().
        InfluenceDiagram diagram = BayesNetworkBuilder.create()
            .variable("disease", "yes", "no").prior(0.4, 0.6)
            .decision("treatment", "treat", "wait")
            .utility("cost").given("treatment").utilities(-5, 0)
            .utility("health").given("disease", "treatment").utilities(
                9, 1,
                4, 8)
            .buildInfluenceDiagram();

        PolicyResult result = InfluenceDiagramEngine.solve(diagram);

        // Same computation as multipleUtilityNodes_additiveAggregation
        assertEquals("wait", result.optimalPolicy().get(Map.of()));
        assertEquals(5.2, result.maxExpectedUtility(), 0.01);
    }

    @Test
    void allDecisionParents_deterministicUtility() {
        InfluenceDiagram diagram = BayesNetworkBuilder.create()
            .decision("action", "A", "B", "C")
            .utility("payoff").given("action").utilities(5, 10, 3)
            .buildInfluenceDiagram();

        PolicyResult result = InfluenceDiagramEngine.solve(diagram);

        assertEquals("B", result.optimalPolicy().get(Map.of()));
        assertEquals(10.0, result.maxExpectedUtility(), 0.01);
    }
}

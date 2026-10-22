package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.graph.GraphNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CausalEngineTest {

    /**
     * Classic confounded network:
     *
     *   Confounder → Treatment
     *   Confounder → Outcome
     *   Treatment  → Outcome
     *
     * Observing Treatment=true selects for Confounder=true (because Confounder
     * causes Treatment), inflating the apparent effect of Treatment on Outcome.
     * do(Treatment=true) severs the Confounder→Treatment edge, removing this
     * confounding bias.
     *
     * With these CPTs:
     *   P(O=true | observe T=true)  ≈ 0.75  (inflated by confounding)
     *   P(O=true | do(T=true))      ≈ 0.55  (true causal effect)
     */
    private BayesNetwork buildConfoundedNetwork() {
        return BayesNetworkBuilder.create("confounded")
                .variable("Confounder", "false", "true")
                    .prior(0.5, 0.5)
                .variable("Treatment", "false", "true")
                    .given("Confounder")
                    .cpt(0.9, 0.1,    // C=false: T mostly false
                         0.1, 0.9)    // C=true:  T mostly true
                .variable("Outcome", "false", "true")
                    .given("Confounder", "Treatment")
                    .cpt(0.8, 0.2,    // C=false, T=false
                         0.7, 0.3,    // C=false, T=true
                         0.3, 0.7,    // C=true,  T=false
                         0.2, 0.8)    // C=true,  T=true
                .build();
    }

    @Test
    void interventionalDiffersFromObservationalWhenConfounded() {
        BayesNetwork network = buildConfoundedNetwork();

        // Observational: P(Outcome | observe Treatment=true)
        BayesInstance<?> observational = new BayesInstance<>(new JunctionTreeBuilder(network).build());
        observational.setHardEvidence("Treatment", "true");
        observational.globalUpdate();
        double pOutcomeObserved = observational.marginalize("Outcome").getDistribution()[1];

        // Interventional: P(Outcome | do(Treatment=true))
        BayesInstance<?> interventional = CausalEngine.intervene(network, "Treatment", "true");
        double pOutcomeIntervened = interventional.marginalize("Outcome").getDistribution()[1];

        // Observational is inflated by confounding: ~0.75 vs ~0.55
        assertEquals(0.75, pOutcomeObserved, 0.01);
        assertEquals(0.55, pOutcomeIntervened, 0.01);
        assertTrue(pOutcomeObserved > pOutcomeIntervened,
                "Observational should overestimate due to confounding");
    }

    @Test
    void interventionalMatchesObservationalWhenUnconfounded() {
        // No confounder — Treatment directly causes Outcome
        BayesNetwork network = BayesNetworkBuilder.create("unconfounded")
                .variable("Treatment", "false", "true")
                    .prior(0.4, 0.6)
                .variable("Outcome", "false", "true")
                    .given("Treatment")
                    .cpt(0.8, 0.2,    // T=false
                         0.3, 0.7)    // T=true
                .build();

        BayesInstance<?> observational = new BayesInstance<>(new JunctionTreeBuilder(network).build());
        observational.setHardEvidence("Treatment", "true");
        observational.globalUpdate();
        double pOutcomeObserved = observational.marginalize("Outcome").getDistribution()[1];

        BayesInstance<?> interventional = CausalEngine.intervene(network, "Treatment", "true");
        double pOutcomeIntervened = interventional.marginalize("Outcome").getDistribution()[1];

        // Both should give P(Outcome=true | T=true) = 0.7
        assertEquals(pOutcomeObserved, pOutcomeIntervened, 1e-9,
                "No confounding — observational and interventional should match");
        assertEquals(0.7, pOutcomeIntervened, 0.01);
    }

    @Test
    void counterfactualSurfacesConfounding() {
        BayesNetwork network = buildConfoundedNetwork();

        CounterfactualResult result = CausalEngine.counterfactual(
                network, "Treatment", "true", "Outcome");

        assertEquals("Treatment", result.interventionVar());
        assertEquals("true", result.state());
        assertEquals("Outcome", result.targetVar());
        assertArrayEquals(new String[]{"false", "true"}, result.targetOutcomes());

        // Observational ~0.75, interventional ~0.55
        assertEquals(0.75, result.observational()[1], 0.01);
        assertEquals(0.55, result.interventional()[1], 0.01);

        // Causal effect: do - observe = negative (confounding inflated the observation)
        double effect = result.causalEffect(1);
        assertTrue(effect < 0, "Causal effect should be negative — confounding inflated observation");
        assertEquals(-0.20, effect, 0.01);

        assertTrue(result.hasConfounding(0.01));
    }

    @Test
    void counterfactualShowsNoConfoundingWhenCausallySufficient() {
        BayesNetwork network = BayesNetworkBuilder.create()
                .variable("A", "false", "true").prior(0.3, 0.7)
                .variable("B", "false", "true")
                    .given("A").cpt(0.6, 0.4, 0.2, 0.8)
                .build();

        CounterfactualResult result = CausalEngine.counterfactual(
                network, "A", "true", "B");

        assertFalse(result.hasConfounding(0.01));
        assertEquals(result.observational()[1], result.interventional()[1], 1e-9);
    }

    @Test
    void multiVariableIntervention() {
        // A → C, B → C, A → D
        BayesNetwork network = BayesNetworkBuilder.create()
                .variable("A", "false", "true").prior(0.5, 0.5)
                .variable("B", "false", "true").prior(0.5, 0.5)
                .variable("C", "false", "true")
                    .given("A", "B")
                    .cpt(0.9, 0.1, 0.6, 0.4, 0.4, 0.6, 0.1, 0.9)
                .variable("D", "false", "true")
                    .given("A").cpt(0.7, 0.3, 0.2, 0.8)
                .build();

        BayesInstance<?> result = CausalEngine.intervene(network,
                Map.of("A", "true", "B", "true"));
        result.globalUpdate();
        double[] cPosterior = result.marginalize("C").getDistribution();
        double[] dPosterior = result.marginalize("D").getDistribution();

        // do(A=true, B=true): C has no parents in mutilated graph,
        // but wait — C's parents are A and B, and both are intervened,
        // so C's parents are severed. C becomes a root with uniform prior.
        // Then hard evidence A=true and B=true are set.
        // Actually, only the parents of A and B are severed, not C's parents.
        // A and B are root nodes already (no parents), so do(A,B) doesn't
        // change the graph structure — it just sets hard evidence.
        // P(C=true | A=true, B=true) = 0.9
        assertEquals(0.9, cPosterior[1], 0.01);
        // P(D=true | A=true) = 0.8
        assertEquals(0.8, dPosterior[1], 0.01);
    }

    @Test
    void singleInterventionConsistentWithMultiVariableMap() {
        BayesNetwork network = buildConfoundedNetwork();

        BayesInstance<?> single = CausalEngine.intervene(network, "Treatment", "true");
        BayesInstance<?> multi = CausalEngine.intervene(network, Map.of("Treatment", "true"));

        double[] singlePosterior = single.marginalize("Outcome").getDistribution();
        double[] multiPosterior = multi.marginalize("Outcome").getDistribution();

        assertArrayEquals(singlePosterior, multiPosterior, 1e-9);
    }

    @Test
    void graphSurgeryDoesNotModifyOriginalNetwork() {
        BayesNetwork network = buildConfoundedNetwork();

        // Count edges before
        int edgesBefore = 0;
        for (GraphNode<BayesVariable> node : network) {
            edgesBefore += node.getInEdges().size();
        }

        CausalEngine.intervene(network, "Treatment", "true");

        // Count edges after — should be unchanged
        int edgesAfter = 0;
        for (GraphNode<BayesVariable> node : network) {
            edgesAfter += node.getInEdges().size();
        }
        assertEquals(edgesBefore, edgesAfter, "Original network must not be modified");

        // Treatment should still have its parent
        BayesVariable treatment = null;
        for (GraphNode<BayesVariable> node : network) {
            if ("Treatment".equals(node.getContent().getName())) {
                treatment = node.getContent();
                break;
            }
        }
        assertNotNull(treatment);
        assertEquals(1, treatment.getGiven().length, "Treatment should still have Confounder as parent");
    }

    @Test
    void invalidVariableNameThrows() {
        BayesNetwork network = buildConfoundedNetwork();

        assertThrows(IllegalArgumentException.class,
                () -> CausalEngine.intervene(network, "NonExistent", "true"));
    }

    @Test
    void invalidStateNameThrows() {
        BayesNetwork network = buildConfoundedNetwork();

        assertThrows(IllegalArgumentException.class,
                () -> CausalEngine.intervene(network, "Treatment", "nonexistent"));
    }

    @Test
    void interveneOnRootNodeIsEquivalentToObservation() {
        // Intervening on a root node (no parents to sever) should give
        // the same result as observing it
        BayesNetwork network = buildConfoundedNetwork();

        BayesInstance<?> observational = new BayesInstance<>(new JunctionTreeBuilder(network).build());
        observational.setHardEvidence("Confounder", "true");
        observational.globalUpdate();
        double[] obsOutcome = observational.marginalize("Outcome").getDistribution();

        BayesInstance<?> interventional = CausalEngine.intervene(network, "Confounder", "true");
        double[] intOutcome = interventional.marginalize("Outcome").getDistribution();

        assertArrayEquals(obsOutcome, intOutcome, 1e-9,
                "Intervening on root node should equal observation");
    }
}

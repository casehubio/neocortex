package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CombinedStructureLearningTest {

    @Test
    void endToEnd_priorWithDataRefinement() {
        var data = generateChain(500, 42L);

        var prior = new NetworkSpec("llm-prior",
            List.of(
                new VariableSpec("A", List.of("0", "1"), "root", SpecStatus.PROPOSED, null),
                new VariableSpec("B", List.of("0", "1"), "middle", SpecStatus.PROPOSED, null),
                new VariableSpec("C", List.of("0", "1"), "leaf", SpecStatus.PROPOSED, null),
                new VariableSpec("D", List.of("0", "1"), "phantom", SpecStatus.PROPOSED, null)),
            List.of(
                new EdgeSpec("A", "B", "LLM: A causes B", SpecStatus.PROPOSED),
                new EdgeSpec("A", "C", "LLM: A causes C", SpecStatus.PROPOSED),
                new EdgeSpec("D", "A", "LLM: D causes A", SpecStatus.PROPOSED)),
            "Chain network");

        // Step 1: Extract constraints — all PROPOSED, so none become hard constraints
        StructuralConstraints constraints = StructuralConstraints.fromSpec(prior);
        assertTrue(constraints.requiredEdges().isEmpty());
        assertTrue(constraints.forbiddenEdges().isEmpty());

        // Step 2: Learn from data
        StructureResult dataResult = StructureLearner.pc(data,
            IndependenceTest.adaptive(0.05), constraints);
        assertNotNull(dataResult.networkSpec());

        // Step 3: Reconcile
        ReconciliationResult result = StructureReconciler.reconcile(
            prior, dataResult.networkSpec(), ConflictPolicy.TRUST_DATA);

        // D→A should be LLM_PROPOSED (D not in data)
        var daResolution = result.edges().stream()
            .filter(e -> e.from().equals("D") && e.to().equals("A"))
            .findFirst();
        assertTrue(daResolution.isPresent(),
            "Expected D→A edge in resolutions, got: " + result.edges());
        assertEquals(EdgeProvenance.LLM_PROPOSED, daResolution.get().provenance());

        // Merged spec should contain all 4 variables (union of prior + data)
        assertEquals(4, result.mergedSpec().variables().size());

        // Every edge should have a resolution
        assertFalse(result.edges().isEmpty());

        // Every edge in the merged spec should have provenance tracked
        for (var edge : result.mergedSpec().edges()) {
            var resolution = result.edges().stream()
                .filter(e -> e.from().equals(edge.from()) && e.to().equals(edge.to()))
                .findFirst();
            assertTrue(resolution.isPresent(),
                "Edge " + edge.from() + "→" + edge.to() + " missing from resolutions");
        }

        // Step 4: Fit CPTs on data-available variables and verify valid network
        NetworkSpec forFitting = result.mergedSpec().withVariableRemoved("D");
        // Remove D's edges too (withVariableRemoved handles this)
        BayesNetwork bn = ParameterLearner.fit(forFitting.toBayesNetwork(), data);
        assertNotNull(bn);
        double ll = ModelValidator.logLikelihood(bn, data);
        assertTrue(Double.isFinite(ll));
        assertTrue(ll < 0);
    }

    private static List<Map<String, String>> generateChain(int n, long seed) {
        var rng = new Random(seed);
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            String a = rng.nextDouble() < 0.5 ? "1" : "0";
            String b = a.equals("1")
                ? (rng.nextDouble() < 0.8 ? "1" : "0")
                : (rng.nextDouble() < 0.2 ? "1" : "0");
            String c = b.equals("1")
                ? (rng.nextDouble() < 0.7 ? "1" : "0")
                : (rng.nextDouble() < 0.3 ? "1" : "0");
            data.add(Map.of("A", a, "B", b, "C", c));
        }
        return data;
    }
}

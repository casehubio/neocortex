package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PcAlgorithmTest {

    @Test
    void twoIndependentVariables_noEdges() {
        var data = generateIndependentAB(500, 42L);
        var result = PcAlgorithm.learn(data, IndependenceTest.adaptive(0.05),
            StructuralConstraints.none());

        assertTrue(result.networkSpec().edges().isEmpty());
        assertEquals("PC", result.algorithm());

        var diag = (StructureDiagnostics.PcDiagnostics) result.diagnostics();
        assertTrue(diag.totalTests() > 0);
        assertTrue(diag.edgesRemoved() > 0);
    }

    @Test
    void chainAtoB_discoversEdge() {
        var data = generateChainAB(500, 42L);
        var result = PcAlgorithm.learn(data, IndependenceTest.adaptive(0.05),
            StructuralConstraints.none());

        assertEquals(1, result.networkSpec().edges().size());
        var edge = result.networkSpec().edges().get(0);
        assertTrue(
            (edge.from().equals("A") && edge.to().equals("B")) ||
            (edge.from().equals("B") && edge.to().equals("A"))
        );
        assertEquals(SpecStatus.PROPOSED, edge.status());
    }

    @Test
    void vStructure_detected() {
        var data = generateVStructure(1000, 42L);
        var result = PcAlgorithm.learn(data, IndependenceTest.adaptive(0.05),
            StructuralConstraints.none());

        var diag = (StructureDiagnostics.PcDiagnostics) result.diagnostics();
        assertTrue(diag.vStructuresFound() > 0);

        var edges = result.networkSpec().edges();
        assertEquals(2, edges.size());
        boolean hasAtoC = edges.stream().anyMatch(
            e -> e.from().equals("A") && e.to().equals("C"));
        boolean hasBtoC = edges.stream().anyMatch(
            e -> e.from().equals("B") && e.to().equals("C"));
        assertTrue(hasAtoC, "Missing A→C edge");
        assertTrue(hasBtoC, "Missing B→C edge");
    }

    @Test
    void requiredEdge_survivesElimination() {
        var data = generateIndependentAB(500, 42L);
        var constraints = new StructuralConstraints(
            Set.of(new StructuralConstraints.Edge("A", "B")),
            Set.of(), Integer.MAX_VALUE);

        var result = PcAlgorithm.learn(data, IndependenceTest.adaptive(0.05),
            constraints);

        boolean hasEdge = result.networkSpec().edges().stream().anyMatch(
            e -> e.from().equals("A") && e.to().equals("B")
              || e.from().equals("B") && e.to().equals("A"));
        assertTrue(hasEdge, "Required edge A→B should survive");
    }

    @Test
    void forbiddenEdge_neverAppears() {
        var data = generateChainAB(500, 42L);
        var constraints = new StructuralConstraints(
            Set.of(),
            Set.of(new StructuralConstraints.Edge("A", "B"),
                   new StructuralConstraints.Edge("B", "A")),
            Integer.MAX_VALUE);

        var result = PcAlgorithm.learn(data, IndependenceTest.adaptive(0.05),
            constraints);

        assertTrue(result.networkSpec().edges().isEmpty());
    }

    @Test
    void singleVariable_noEdges() {
        var data = List.of(
            Map.of("X", "a"),
            Map.of("X", "b"),
            Map.of("X", "a")
        );
        var result = PcAlgorithm.learn(data, IndependenceTest.adaptive(0.05),
            StructuralConstraints.none());

        assertEquals(1, result.networkSpec().variables().size());
        assertTrue(result.networkSpec().edges().isEmpty());
    }

    @Test
    void emptyData_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            PcAlgorithm.learn(List.of(), IndependenceTest.adaptive(0.05),
                StructuralConstraints.none()));
    }

    @Test
    void variablesHaveProposedStatus() {
        var data = generateChainAB(200, 42L);
        var result = PcAlgorithm.learn(data, IndependenceTest.adaptive(0.05),
            StructuralConstraints.none());

        for (var v : result.networkSpec().variables()) {
            assertEquals(SpecStatus.PROPOSED, v.status());
        }
    }

    @Test
    void threeVariableChain_findsTwoEdges() {
        var data = generateChainABC(1000, 42L);
        var result = PcAlgorithm.learn(data, IndependenceTest.adaptive(0.05),
            StructuralConstraints.none());

        assertEquals(2, result.networkSpec().edges().size(),
            "Chain A→B→C should produce 2 edges");
    }

    // -- Data generators --

    private List<Map<String, String>> generateIndependentAB(int n, long seed) {
        var rng = new Random(seed);
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            data.add(Map.of("A", rng.nextBoolean() ? "a0" : "a1",
                            "B", rng.nextBoolean() ? "b0" : "b1"));
        }
        return data;
    }

    private List<Map<String, String>> generateChainAB(int n, long seed) {
        var rng = new Random(seed);
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            String a = rng.nextBoolean() ? "a0" : "a1";
            String b = (a.equals("a0"))
                ? (rng.nextDouble() < 0.9 ? "b0" : "b1")
                : (rng.nextDouble() < 0.9 ? "b1" : "b0");
            data.add(Map.of("A", a, "B", b));
        }
        return data;
    }

    private List<Map<String, String>> generateVStructure(int n, long seed) {
        // A→C←B: A,B independent; C = noisy-OR of A,B (asymmetric, preserves marginal deps)
        var rng = new Random(seed);
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            String a = rng.nextBoolean() ? "a0" : "a1";
            String b = rng.nextBoolean() ? "b0" : "b1";
            double pC0;
            if (a.equals("a0") && b.equals("b0")) pC0 = 0.9;
            else if (a.equals("a1") && b.equals("b1")) pC0 = 0.1;
            else if (a.equals("a1")) pC0 = 0.3;
            else pC0 = 0.3;
            String c = rng.nextDouble() < pC0 ? "c0" : "c1";
            data.add(Map.of("A", a, "B", b, "C", c));
        }
        return data;
    }

    private List<Map<String, String>> generateChainABC(int n, long seed) {
        var rng = new Random(seed);
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            String a = rng.nextBoolean() ? "a0" : "a1";
            String b = (a.equals("a0"))
                ? (rng.nextDouble() < 0.85 ? "b0" : "b1")
                : (rng.nextDouble() < 0.85 ? "b1" : "b0");
            String c = (b.equals("b0"))
                ? (rng.nextDouble() < 0.85 ? "c0" : "c1")
                : (rng.nextDouble() < 0.85 ? "c1" : "c0");
            data.add(Map.of("A", a, "B", b, "C", c));
        }
        return data;
    }
}

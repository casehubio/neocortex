package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HillClimbingSearchTest {

    @Test
    void emptyData_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            HillClimbingSearch.learn(List.of(), StructuralConstraints.none(), 1, 42L));
    }

    @Test
    void independentVariables_noEdges() {
        var data = generateIndependentAB(500, 42L);
        var result = HillClimbingSearch.learn(data,
            StructuralConstraints.none(), 3, 42L);

        assertTrue(result.networkSpec().edges().isEmpty(),
            "BIC should penalise edges between independent variables");
        assertEquals("HillClimbing", result.algorithm());
    }

    @Test
    void dependentVariables_discoversEdge() {
        var data = generateChainAB(500, 42L);
        var result = HillClimbingSearch.learn(data,
            StructuralConstraints.none(), 3, 42L);

        assertEquals(1, result.networkSpec().edges().size());
        var edge = result.networkSpec().edges().get(0);
        assertTrue(
            (edge.from().equals("A") && edge.to().equals("B")) ||
            (edge.from().equals("B") && edge.to().equals("A")));
    }

    @Test
    void diagnostics_reportsRestarts() {
        var data = generateChainAB(200, 42L);
        var result = HillClimbingSearch.learn(data,
            StructuralConstraints.none(), 5, 42L);

        var diag = (StructureDiagnostics.HillClimbingDiagnostics) result.diagnostics();
        assertEquals(5, diag.restartCount());
        assertEquals(5, diag.restartScores().size());
        assertTrue(diag.totalOperations() > 0);
        assertTrue(diag.bestRestartIndex() >= 0 && diag.bestRestartIndex() < 5);
    }

    @Test
    void requiredEdge_alwaysPresent() {
        var data = generateIndependentAB(500, 42L);
        var constraints = new StructuralConstraints(
            Set.of(new StructuralConstraints.Edge("A", "B")),
            Set.of(), Integer.MAX_VALUE);

        var result = HillClimbingSearch.learn(data, constraints, 3, 42L);

        boolean hasEdge = result.networkSpec().edges().stream().anyMatch(
            e -> e.from().equals("A") && e.to().equals("B"));
        assertTrue(hasEdge, "Required edge A→B must be present");
    }

    @Test
    void forbiddenEdge_neverAppears() {
        var data = generateChainAB(500, 42L);
        var constraints = new StructuralConstraints(
            Set.of(),
            Set.of(new StructuralConstraints.Edge("A", "B"),
                   new StructuralConstraints.Edge("B", "A")),
            Integer.MAX_VALUE);

        var result = HillClimbingSearch.learn(data, constraints, 3, 42L);
        assertTrue(result.networkSpec().edges().isEmpty());
    }

    @Test
    void maxParents_respected() {
        var data = generateMultiParent(500, 42L);
        var constraints = StructuralConstraints.maxParents(1);

        var result = HillClimbingSearch.learn(data, constraints, 3, 42L);

        var parentCounts = new java.util.HashMap<String, Integer>();
        for (var edge : result.networkSpec().edges()) {
            parentCounts.merge(edge.to(), 1, Integer::sum);
        }
        for (int count : parentCounts.values()) {
            assertTrue(count <= 1, "No variable should have > 1 parent");
        }
    }

    @Test
    void restarts_monotonicallyImprove() {
        var data = generateChainAB(200, 42L);
        var singleRestart = HillClimbingSearch.learn(data,
            StructuralConstraints.none(), 1, 42L);
        var multiRestart = HillClimbingSearch.learn(data,
            StructuralConstraints.none(), 10, 42L);

        var singleDiag = (StructureDiagnostics.HillClimbingDiagnostics)
            singleRestart.diagnostics();
        var multiDiag = (StructureDiagnostics.HillClimbingDiagnostics)
            multiRestart.diagnostics();

        assertTrue(multiDiag.bestScore() <= singleDiag.bestScore() + 1e-10,
            "Multiple restarts should find score >= single restart");
    }

    @Test
    void singleVariable_noEdges() {
        var data = List.of(Map.of("X", "a"), Map.of("X", "b"), Map.of("X", "a"));
        var result = HillClimbingSearch.learn(data,
            StructuralConstraints.none(), 1, 42L);
        assertTrue(result.networkSpec().edges().isEmpty());
    }

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

    private List<Map<String, String>> generateMultiParent(int n, long seed) {
        var rng = new Random(seed);
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            String a = rng.nextBoolean() ? "a0" : "a1";
            String b = rng.nextBoolean() ? "b0" : "b1";
            String c = (a.equals("a0"))
                ? (rng.nextDouble() < 0.8 ? "c0" : "c1")
                : (rng.nextDouble() < 0.8 ? "c1" : "c0");
            data.add(Map.of("A", a, "B", b, "C", c));
        }
        return data;
    }
}

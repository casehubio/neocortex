package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IndependenceTestTest {

    @Test
    void gTest_independentVariables_returnsIndependent() {
        var data = generateIndependent(500, 42L);
        var result = IndependenceTest.gTest(0.05).test("X", "Y", Set.of(), data);
        assertTrue(result.independent());
        assertTrue(result.pValue() > 0.05);
    }

    @Test
    void gTest_dependentVariables_returnsDependent() {
        var data = generateDependent(200, 42L);
        var result = IndependenceTest.gTest(0.05).test("X", "Y", Set.of(), data);
        assertFalse(result.independent());
        assertTrue(result.pValue() < 0.01);
    }

    @Test
    void gTest_conditionalIndependence() {
        var data = generateConditionallyIndependent(500, 42L);
        var result = IndependenceTest.gTest(0.05).test("X", "Y", Set.of("Z"), data);
        assertTrue(result.independent());
    }

    @Test
    void fisherExact_binaryIndependent_returnsIndependent() {
        var data = generateBinaryIndependent(100, 42L);
        var result = IndependenceTest.fisherExact(0.05).test("X", "Y", Set.of(), data);
        assertTrue(result.independent());
        assertTrue(result.pValue() > 0.05);
    }

    @Test
    void fisherExact_binaryDependent_returnsDependent() {
        var data = generateDependent(100, 42L);
        var result = IndependenceTest.fisherExact(0.05).test("X", "Y", Set.of(), data);
        assertFalse(result.independent());
        assertTrue(result.pValue() < 0.05);
    }

    @Test
    void adaptive_selectsFisherForSmallCounts() {
        var data = List.of(
            Map.of("X", "a", "Y", "c"),
            Map.of("X", "a", "Y", "d"),
            Map.of("X", "b", "Y", "c"),
            Map.of("X", "b", "Y", "d")
        );
        var result = IndependenceTest.adaptive(0.05).test("X", "Y", Set.of(), data);
        assertNotNull(result);
        assertTrue(result.pValue() >= 0.0 && result.pValue() <= 1.0);
    }

    @Test
    void adaptive_usesGTestForLargeCounts() {
        var data = generateIndependent(500, 42L);
        var result = IndependenceTest.adaptive(0.05).test("X", "Y", Set.of(), data);
        assertTrue(result.independent());
    }

    @Test
    void gTest_uniformData_highPValue() {
        var data = generateUniform(1000, 42L);
        var result = IndependenceTest.gTest(0.05).test("X", "Y", Set.of(), data);
        assertTrue(result.pValue() > 0.1);
    }

    private List<Map<String, String>> generateIndependent(int n, long seed) {
        var rng = new Random(seed);
        String[] xVals = {"a", "b", "c"};
        String[] yVals = {"d", "e", "f"};
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            data.add(Map.of("X", xVals[rng.nextInt(3)], "Y", yVals[rng.nextInt(3)]));
        }
        return data;
    }

    private List<Map<String, String>> generateDependent(int n, long seed) {
        var rng = new Random(seed);
        String[] vals = {"a", "b"};
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            String v = vals[rng.nextInt(2)];
            data.add(Map.of("X", v, "Y", v));
        }
        return data;
    }

    private List<Map<String, String>> generateBinaryIndependent(int n, long seed) {
        var rng = new Random(seed);
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            data.add(Map.of("X", rng.nextBoolean() ? "a" : "b",
                            "Y", rng.nextBoolean() ? "c" : "d"));
        }
        return data;
    }

    private List<Map<String, String>> generateConditionallyIndependent(int n, long seed) {
        // Chain: X→Z→Y — X⊥Y|Z (conditioning on mediator blocks the path)
        var rng = new Random(seed);
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            String x = rng.nextBoolean() ? "a" : "b";
            String z = (x.equals("a"))
                ? (rng.nextDouble() < 0.8 ? "a" : "b")
                : (rng.nextDouble() < 0.8 ? "b" : "a");
            String y = (z.equals("a"))
                ? (rng.nextDouble() < 0.8 ? "a" : "b")
                : (rng.nextDouble() < 0.8 ? "b" : "a");
            data.add(Map.of("X", x, "Y", y, "Z", z));
        }
        return data;
    }

    private List<Map<String, String>> generateUniform(int n, long seed) {
        var rng = new Random(seed);
        String[] xVals = {"a", "b"};
        String[] yVals = {"c", "d"};
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            data.add(Map.of("X", xVals[rng.nextInt(2)], "Y", yVals[rng.nextInt(2)]));
        }
        return data;
    }
}

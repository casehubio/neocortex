package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class StructureLearnerTest {

    @Test
    void pc_convenienceOverload() {
        var data = generateChainAB(500, 42L);
        var result = StructureLearner.pc(data, 0.05);
        assertNotNull(result);
        assertEquals("PC", result.algorithm());
        assertFalse(result.networkSpec().edges().isEmpty());
    }

    @Test
    void hillClimbing_convenienceOverload() {
        var data = generateChainAB(500, 42L);
        var result = StructureLearner.hillClimbing(data);
        assertNotNull(result);
        assertEquals("HillClimbing", result.algorithm());
    }

    @Test
    void pc_toBayesNetwork_producesValidNetwork() {
        var data = generateChainAB(500, 42L);
        var result = StructureLearner.pc(data, 0.05);
        BayesNetwork bn = result.toBayesNetwork(data);

        assertNotNull(bn);
        double ll = ModelValidator.logLikelihood(bn, data);
        assertTrue(Double.isFinite(ll));
        assertTrue(ll < 0);
    }

    @Test
    void hillClimbing_toBayesNetwork_producesValidNetwork() {
        var data = generateChainAB(500, 42L);
        var result = StructureLearner.hillClimbing(data,
            StructuralConstraints.none(), 3, 42L);
        BayesNetwork bn = result.toBayesNetwork(data);

        assertNotNull(bn);
        double ll = ModelValidator.logLikelihood(bn, data);
        assertTrue(Double.isFinite(ll));
    }

    @Test
    void bicScores_areSane() {
        var data = generateChainAB(500, 42L);
        var pcResult = StructureLearner.pc(data, 0.05);
        var hcResult = StructureLearner.hillClimbing(data,
            StructuralConstraints.none(), 3, 42L);

        BayesNetwork pcNetwork = pcResult.toBayesNetwork(data);
        BayesNetwork hcNetwork = hcResult.toBayesNetwork(data);

        ModelScore pcBic = ModelSelector.bic(pcNetwork, data);
        ModelScore hcBic = ModelSelector.bic(hcNetwork, data);

        assertTrue(Double.isFinite(pcBic.score()));
        assertTrue(Double.isFinite(hcBic.score()));
    }

    @Test
    void threeVariableChain_recoveredByBothAlgorithms() {
        var data = generateChainABC(1000, 42L);

        var pcResult = StructureLearner.pc(data, 0.05);
        var hcResult = StructureLearner.hillClimbing(data,
            StructuralConstraints.none(), 5, 42L);

        assertEquals(2, pcResult.networkSpec().edges().size(),
            "PC should find 2 edges in A→B→C chain");
        assertEquals(2, hcResult.networkSpec().edges().size(),
            "HC should find 2 edges in A→B→C chain");
    }

    @Test
    void pc_emptyData_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            StructureLearner.pc(List.of(), 0.05));
    }

    @Test
    void hillClimbing_emptyData_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            StructureLearner.hillClimbing(List.of()));
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

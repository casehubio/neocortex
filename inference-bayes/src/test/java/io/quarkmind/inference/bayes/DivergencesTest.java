package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DivergencesTest {

    @Test
    void klDivergence_identicalDistributions_returnsZero() {
        double[] p = {0.5, 0.3, 0.2};
        assertEquals(0.0, Divergences.klDivergence(p, p.clone()), 1e-10);
    }

    @Test
    void klDivergence_differentDistributions_positive() {
        double[] p = {0.9, 0.1};
        double[] q = {0.5, 0.5};
        double kl = Divergences.klDivergence(p, q);
        assertTrue(kl > 0);
        double expected = 0.9 * Math.log(0.9 / 0.5) + 0.1 * Math.log(0.1 / 0.5);
        assertEquals(expected, kl, 1e-10);
    }

    @Test
    void klDivergence_zeroInQ_returnsInfinity() {
        double[] p = {0.5, 0.5};
        double[] q = {1.0, 0.0};
        assertEquals(Double.POSITIVE_INFINITY, Divergences.klDivergence(p, q));
    }

    @Test
    void klDivergence_zeroInP_skipsEntry() {
        double[] p = {1.0, 0.0};
        double[] q = {0.5, 0.5};
        double kl = Divergences.klDivergence(p, q);
        double expected = 1.0 * Math.log(1.0 / 0.5);
        assertEquals(expected, kl, 1e-10);
    }

    @Test
    void klDivergence_isAsymmetric() {
        double[] p = {0.9, 0.1};
        double[] q = {0.5, 0.5};
        assertNotEquals(Divergences.klDivergence(p, q),
                        Divergences.klDivergence(q, p), 1e-10);
    }

    @Test
    void totalVariation_identicalDistributions_returnsZero() {
        double[] p = {0.5, 0.3, 0.2};
        assertEquals(0.0, Divergences.totalVariation(p, p.clone()), 1e-10);
    }

    @Test
    void totalVariation_maximallyDivergent_returnsOne() {
        double[] p = {1.0, 0.0};
        double[] q = {0.0, 1.0};
        assertEquals(1.0, Divergences.totalVariation(p, q), 1e-10);
    }

    @Test
    void totalVariation_symmetric() {
        double[] p = {0.7, 0.3};
        double[] q = {0.4, 0.6};
        assertEquals(Divergences.totalVariation(p, q),
                     Divergences.totalVariation(q, p), 1e-10);
    }

    @Test
    void totalVariation_knownValue() {
        double[] p = {0.7, 0.3};
        double[] q = {0.4, 0.6};
        assertEquals(0.3, Divergences.totalVariation(p, q), 1e-10);
    }

    @Test
    void compute_producesCorrectRecord() {
        double[] pA = {0.8, 0.2};
        double[] pB = {0.5, 0.5};
        BeliefDivergence div = Divergences.compute("alice", "bob", "X", pA, pB);
        assertEquals("alice", div.agentA());
        assertEquals("bob", div.agentB());
        assertEquals("X", div.variable());
        assertEquals(Divergences.klDivergence(pA, pB), div.klDivergence(), 1e-10);
        assertEquals(Divergences.klDivergence(pB, pA), div.klDivergenceReverse(), 1e-10);
        assertEquals(Divergences.totalVariation(pA, pB), div.totalVariation(), 1e-10);
    }
}

package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static io.quarkmind.inference.bayes.IncrementalUpdateTest.assertSameMarginals;

/**
 * Refs #631 — MAP inference via max-product message passing.
 */
public class MapInferenceTest {

    @Test
    void mapInference_matchesBruteForceOnSmallChain() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        MapAssignment map = instance.mapInference();

        // Chain of 4 binary vars: P(x0) = [0.5, 0.5], P(xi|xi-1) = [[0.7, 0.3], [0.4, 0.6]]
        // Brute force: FFFF = 0.5*0.7*0.7*0.7 = 0.1715 — highest of all 16 assignments
        assertThat(map.assignment()).containsEntry("var_0", "false");
        assertThat(map.assignment()).containsEntry("var_1", "false");
        assertThat(map.assignment()).containsEntry("var_2", "false");
        assertThat(map.assignment()).containsEntry("var_3", "false");
    }

    @Test
    void mapInference_matchesBruteForceOnLargerChain() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        MapAssignment map = instance.mapInference();

        // For chain with CPT [[0.7, 0.3], [0.4, 0.6]], the MAP is all-false:
        // each step multiplies by 0.7 (F→F) vs max alternative
        for (int i = 0; i < 8; i++) {
            assertThat(map.assignment())
                    .as("var_%d", i)
                    .containsEntry(BayesNetworkFixtures.varName(i), "false");
        }
    }

    @Test
    void mapInference_withHardEvidence_respectsConstraint() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setHardEvidence("var_0", "true");
        instance.setHardEvidence("var_3", "true");

        MapAssignment map = instance.mapInference();

        // With var_0=T, var_3=T fixed, brute force over var_1, var_2:
        // TTTT: 0.6*0.6*0.6 = 0.216  ← MAP
        // TTFT: 0.6*0.4*0.3 = 0.072
        // TFTT: 0.4*0.3*0.6 = 0.072
        // TFFT: 0.4*0.7*0.3 = 0.084
        assertThat(map.assignment()).containsEntry("var_0", "true");
        assertThat(map.assignment()).containsEntry("var_1", "true");
        assertThat(map.assignment()).containsEntry("var_2", "true");
        assertThat(map.assignment()).containsEntry("var_3", "true");
    }

    @Test
    void mapInference_withSoftEvidence_shiftsAssignment() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        // Strong evidence toward true on var_0
        instance.setLikelyhood("var_0", new double[]{0.1, 0.9});

        MapAssignment map = instance.mapInference();

        // With var_0 strongly biased to true, MAP shifts to all-true:
        // TTTT: (0.5*0.9)*0.6*0.6*0.6 = 0.45*0.216 = 0.0972
        // FFFF: (0.5*0.1)*0.7*0.7*0.7 = 0.05*0.343 = 0.01715
        // So var_0=T dominates, and T→T transitions (0.6) cascade
        assertThat(map.assignment()).containsEntry("var_0", "true");
    }

    @Test
    void mapInference_doesNotCorruptMarginalState() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);
        BayesInstance<?> reference = new BayesInstance<>(jt);

        instance.setLikelyhood("var_0", new double[]{0.8, 0.2});
        reference.setLikelyhood("var_0", new double[]{0.8, 0.2});

        // Run marginal inference
        instance.globalUpdate();
        reference.globalUpdate();
        assertSameMarginals(instance, reference, 4);

        // Run MAP — should not corrupt marginal state
        instance.mapInference();

        // Marginals should still match
        assertSameMarginals(instance, reference, 4);
    }

    @Test
    void mapInference_noEvidence_returnsMapOfPrior() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(3);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        MapAssignment map = instance.mapInference();

        // No evidence: MAP of prior
        // FFF: 0.5*0.7*0.7 = 0.245  ← MAP
        // FFT: 0.5*0.7*0.3 = 0.105
        // FTF: 0.5*0.3*0.4 = 0.06
        // FTT: 0.5*0.3*0.6 = 0.09
        // TFF: 0.5*0.4*0.7 = 0.14
        // TFT: 0.5*0.4*0.3 = 0.06
        // TTF: 0.5*0.6*0.4 = 0.12
        // TTT: 0.5*0.6*0.6 = 0.18
        assertThat(map.assignment()).containsEntry("var_0", "false");
        assertThat(map.assignment()).containsEntry("var_1", "false");
        assertThat(map.assignment()).containsEntry("var_2", "false");
    }

    @Test
    void mapInference_assignmentContainsAllVariables() {
        int n = 6;
        JunctionTree jt = BayesNetworkFixtures.buildChain(n);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        MapAssignment map = instance.mapInference();

        assertThat(map.assignment()).hasSize(n);
        for (int i = 0; i < n; i++) {
            assertThat(map.assignment()).containsKey(BayesNetworkFixtures.varName(i));
        }
    }

    @Test
    void mapInference_matchesBruteForceEnumeration() {
        int n = 4;
        JunctionTree jt = BayesNetworkFixtures.buildChain(n);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("var_1", new double[]{0.3, 0.7});

        MapAssignment map = instance.mapInference();

        // Brute force enumeration with evidence on var_1
        double bestProb = Double.NEGATIVE_INFINITY;
        int[] bestAssignment = null;
        for (int bits = 0; bits < (1 << n); bits++) {
            int[] states = new int[n];
            for (int i = 0; i < n; i++) {
                states[i] = (bits >> i) & 1;
            }
            double logProb = bruteForceLogJoint(states, n);
            if (logProb > bestProb) {
                bestProb = logProb;
                bestAssignment = states.clone();
            }
        }

        for (int i = 0; i < n; i++) {
            String expected = bestAssignment[i] == 0 ? "false" : "true";
            assertThat(map.assignment())
                    .as("var_%d: brute-force MAP = %s", i, expected)
                    .containsEntry(BayesNetworkFixtures.varName(i), expected);
        }
    }

    private double bruteForceLogJoint(int[] states, int n) {
        // P(x0) = [0.5, 0.5]
        // P(xi|xi-1) = [[0.7, 0.3], [0.4, 0.6]]
        // Evidence on var_1: [0.3, 0.7]
        double logP = Math.log(0.5); // prior for var_0

        // evidence on var_1
        double[] ev1 = {0.3, 0.7};
        logP += Math.log(ev1[states[1]]);

        for (int i = 1; i < n; i++) {
            double[][] cpt = {{0.7, 0.3}, {0.4, 0.6}};
            logP += Math.log(cpt[states[i - 1]][states[i]]);
        }
        return logP;
    }
}

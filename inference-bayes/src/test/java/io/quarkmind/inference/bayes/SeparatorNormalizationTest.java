package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static io.quarkmind.inference.bayes.IncrementalUpdateTest.assertSameMarginals;

/**
 * Refs #623 — separator normalization prevents numerical drift.
 */
public class SeparatorNormalizationTest {

    @Test
    void retraction_accuracy_stable_after_1000_cycles() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(16);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> retract = new BayesInstance<>(jt, true);

        double[][] evidenceSequence = {
                {0.8, 0.2}, {0.3, 0.7}, {0.6, 0.4}, {0.9, 0.1}, {0.5, 0.5}
        };

        for (int cycle = 0; cycle < 1000; cycle++) {
            double[] ev = evidenceSequence[cycle % evidenceSequence.length];
            forced.setLikelyhood(BayesNetworkFixtures.varName(0), ev);
            retract.setLikelyhood(BayesNetworkFixtures.varName(0), ev);
            forced.globalUpdateForced();
            retract.globalUpdate();
        }

        // After 1000 cycles, retraction should still match forced baseline
        assertSameMarginals(forced, retract, 16);
    }

    @Test
    void retraction_accuracy_stable_with_multiple_evidence_variables() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(16);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> retract = new BayesInstance<>(jt, true);

        for (int cycle = 0; cycle < 500; cycle++) {
            double bias = 0.5 + 0.4 * Math.sin(cycle * 0.1);
            forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{bias, 1.0 - bias});
            forced.setLikelyhood(BayesNetworkFixtures.varName(8), new double[]{1.0 - bias, bias});
            retract.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{bias, 1.0 - bias});
            retract.setLikelyhood(BayesNetworkFixtures.varName(8), new double[]{1.0 - bias, bias});
            forced.globalUpdateForced();
            retract.globalUpdate();
        }

        assertSameMarginals(forced, retract, 16);
    }

    @Test
    void clique_potentials_remain_finite_after_many_cycles() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(16);
        BayesInstance<?> instance = new BayesInstance<>(jt, true);

        double[][] evidenceSequence = {
                {0.9, 0.1}, {0.1, 0.9}, {0.7, 0.3}, {0.3, 0.7}
        };

        for (int cycle = 0; cycle < 1000; cycle++) {
            double[] ev = evidenceSequence[cycle % evidenceSequence.length];
            instance.setLikelyhood(BayesNetworkFixtures.varName(0), ev);
            instance.globalUpdate();
        }

        for (CliqueState cs : instance.getCliqueStates()) {
            for (double v : cs.getPotentials()) {
                assertThat(v).isNotNaN();
                assertThat(Double.isInfinite(v) && v > 0).as("positive infinity in clique").isFalse();
            }
        }
        for (SeparatorState ss : instance.getSeparatorStates()) {
            for (double v : ss.getPotentials()) {
                assertThat(v).isNotNaN();
                assertThat(Double.isInfinite(v) && v > 0).as("positive infinity in separator").isFalse();
            }
        }
    }

    @Test
    void klDivergence_from_ground_truth_stays_low_over_cycles() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(16);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> retract = new BayesInstance<>(jt, true);

        double maxKl = 0.0;
        for (int cycle = 0; cycle < 200; cycle++) {
            double bias = 0.5 + 0.4 * Math.sin(cycle * 0.2);
            forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{bias, 1.0 - bias});
            retract.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{bias, 1.0 - bias});
            forced.globalUpdateForced();
            retract.globalUpdate();

            double[] p = forced.marginalize(BayesNetworkFixtures.varName(15)).getDistribution();
            double[] q = retract.marginalize(BayesNetworkFixtures.varName(15)).getDistribution();
            double kl = BayesInstance.klDivergence(p, q);
            if (kl > maxKl) maxKl = kl;
        }

        assertThat(maxKl).as("max KL divergence over 200 cycles").isLessThan(1e-6);
    }
}

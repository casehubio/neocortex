package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import static io.quarkmind.inference.bayes.IncrementalUpdateTest.assertSameMarginals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Refs #624 — epsilon-clamped division prevents NaN/Infinity from zero denominators.
 */
public class EpsilonClampTest {

    @Test
    void hardEvidence_thenRetract_producesValidPosteriors() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> retract = new BayesInstance<>(jt, true);

        // Set hard 0/1 evidence
        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{1.0, 0.0});
        retract.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{1.0, 0.0});
        forced.globalUpdateForced();
        retract.globalUpdate();
        assertSameMarginals(forced, retract, 8);

        // Retract hard evidence back to soft — this triggers division by values
        // that were zero-clamped. Without epsilon guard, produces NaN/Infinity.
        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        retract.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        forced.globalUpdateForced();
        retract.globalUpdate();
        assertSameMarginals(forced, retract, 8);
        assertNoNanOrInfinity(retract, 8);
    }

    @Test
    void hardEvidence_onMultipleVariables_thenRetract_producesValidPosteriors() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> retract = new BayesInstance<>(jt, true);

        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{1.0, 0.0});
        forced.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.0, 1.0});
        retract.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{1.0, 0.0});
        retract.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.0, 1.0});
        forced.globalUpdateForced();
        retract.globalUpdate();
        assertSameMarginals(forced, retract, 8);

        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.5, 0.5});
        forced.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.5, 0.5});
        retract.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.5, 0.5});
        retract.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.5, 0.5});
        forced.globalUpdateForced();
        retract.globalUpdate();
        assertSameMarginals(forced, retract, 8);
        assertNoNanOrInfinity(retract, 8);
    }

    @Test
    void hardEvidence_toggleBetweenStates_producesValidPosteriors() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> retract = new BayesInstance<>(jt, true);

        double[][] sequence = {
                {1.0, 0.0},
                {0.0, 1.0},
                {1.0, 0.0},
                {0.7, 0.3}
        };

        for (double[] ev : sequence) {
            forced.setLikelyhood(BayesNetworkFixtures.varName(0), ev);
            retract.setLikelyhood(BayesNetworkFixtures.varName(0), ev);
            forced.globalUpdateForced();
            retract.globalUpdate();
            assertSameMarginals(forced, retract, 4);
            assertNoNanOrInfinity(retract, 4);
        }
    }

    @Test
    void dividePotentials_inLogSpace_subtractsCorrectly() {
        double[] potentials    = {Math.log(0.5), Math.log(0.3), Math.log(0.2)};
        double[] oldPotentials = {Math.log(0.5), Math.log(0.5), Math.log(0.5)};

        double[] result = BayesAbsorption.dividePotentials(potentials, oldPotentials);

        assertThat(Math.exp(result[0])).isCloseTo(1.0, within(1e-10));
        assertThat(Math.exp(result[1])).isCloseTo(0.6, within(1e-10));
        assertThat(Math.exp(result[2])).isCloseTo(0.4, within(1e-10));
        for (double v : result) {
            assertThat(v).isNotNaN();
            assertThat(v).isNotIn(Double.POSITIVE_INFINITY);
        }
    }

    private static void assertNoNanOrInfinity(BayesInstance<?> instance, int n) {
        for (int i = 0; i < n; i++) {
            double[] dist = instance.marginalize(BayesNetworkFixtures.varName(i)).getDistribution();
            for (int j = 0; j < dist.length; j++) {
                assertThat(dist[j])
                        .as("var_%d[%d]", i, j)
                        .isNotNaN()
                        .isNotIn(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
            }
        }
    }
}

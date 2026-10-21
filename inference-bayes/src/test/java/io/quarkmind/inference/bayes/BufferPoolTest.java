package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import static io.quarkmind.inference.bayes.IncrementalUpdateTest.assertSameMarginals;

/**
 * Refs #621 — verifies buffer pooling correctness across repeated globalUpdate
 * calls with changing evidence. Allocation-free propagation is verified via
 * profiler (-prof gc); these tests verify numerical correctness.
 */
public class BufferPoolTest {

    @Test
    void repeatedUpdates_matchForcedBaseline() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> pooled = new BayesInstance<>(jt);
        BayesInstance<?> forced = new BayesInstance<>(jt);

        double[][] evidenceSequence = {
                {0.8, 0.2}, {0.3, 0.7}, {0.5, 0.5}, {0.1, 0.9}, {0.6, 0.4}
        };

        for (double[] evidence : evidenceSequence) {
            pooled.setLikelyhood(BayesNetworkFixtures.varName(0), evidence);
            forced.setLikelyhood(BayesNetworkFixtures.varName(0), evidence);

            pooled.globalUpdate();
            forced.globalUpdateForced();

            assertSameMarginals(pooled, forced, 8);
        }
    }

    @Test
    void repeatedUpdates_multipleVariables_matchForcedBaseline() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> pooled = new BayesInstance<>(jt);
        BayesInstance<?> forced = new BayesInstance<>(jt);

        for (int round = 0; round < 10; round++) {
            double bias = 0.1 * (round + 1);
            pooled.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{bias, 1.0 - bias});
            forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{bias, 1.0 - bias});
            pooled.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{1.0 - bias, bias});
            forced.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{1.0 - bias, bias});

            pooled.globalUpdate();
            forced.globalUpdateForced();

            assertSameMarginals(pooled, forced, 8);
        }
    }

    @Test
    void fastRetract_withBufferPool_matchesForcedBaseline() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> pooled = new BayesInstance<>(jt, true);
        BayesInstance<?> forced = new BayesInstance<>(jt);

        pooled.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        pooled.globalUpdate();
        forced.globalUpdateForced();
        assertSameMarginals(pooled, forced, 8);

        // Change evidence — triggers retraction path
        pooled.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.3, 0.7});
        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.3, 0.7});
        pooled.globalUpdate();
        forced.globalUpdateForced();
        assertSameMarginals(pooled, forced, 8);
    }
}

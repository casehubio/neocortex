package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import static io.quarkmind.inference.bayes.IncrementalUpdateTest.assertSameMarginals;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refs #625 — parallel subtree propagation via virtual threads.
 */
public class ParallelPropagationTest {

    @Test
    void parallel_matchesSequentialOnChain() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> seq = new BayesInstance<>(jt);
        BayesInstance<?> par = new BayesInstance<>(jt);
        par.setParallelEnabled(true);

        seq.setLikelyhood("var_0", new double[]{0.8, 0.2});
        par.setLikelyhood("var_0", new double[]{0.8, 0.2});

        seq.globalUpdate();
        par.globalUpdate();

        assertSameMarginals(seq, par, 8);
    }

    @Test
    void parallel_matchesSequentialOnStar() {
        int nLeaves = 10;
        JunctionTree jt = BayesNetworkFixtures.buildStar(nLeaves);
        BayesInstance<?> seq = new BayesInstance<>(jt);
        BayesInstance<?> par = new BayesInstance<>(jt);
        par.setParallelEnabled(true);

        seq.setLikelyhood("var_0", new double[]{0.3, 0.7});
        par.setLikelyhood("var_0", new double[]{0.3, 0.7});

        for (int i = 1; i <= 5; i++) {
            seq.setLikelyhood("var_" + i, new double[]{0.6, 0.4});
            par.setLikelyhood("var_" + i, new double[]{0.6, 0.4});
        }

        seq.globalUpdate();
        par.globalUpdate();

        assertSameMarginals(seq, par, nLeaves + 1);
    }

    @Test
    void parallel_repeatedUpdates_matchSequential() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> seq = new BayesInstance<>(jt);
        BayesInstance<?> par = new BayesInstance<>(jt);
        par.setParallelEnabled(true);

        double[][] evidenceSequence = {
                {0.8, 0.2}, {0.3, 0.7}, {0.5, 0.5}, {0.1, 0.9}
        };

        for (double[] ev : evidenceSequence) {
            seq.setLikelyhood("var_0", ev);
            par.setLikelyhood("var_0", ev);

            seq.globalUpdate();
            par.globalUpdate();

            assertSameMarginals(seq, par, 8);
        }
    }

    @Test
    void parallel_withFastRetract_matchesSequential() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> seq = new BayesInstance<>(jt, true);
        BayesInstance<?> par = new BayesInstance<>(jt, true);
        par.setParallelEnabled(true);

        seq.setLikelyhood("var_0", new double[]{0.8, 0.2});
        par.setLikelyhood("var_0", new double[]{0.8, 0.2});
        seq.globalUpdate();
        par.globalUpdate();
        assertSameMarginals(seq, par, 8);

        seq.setLikelyhood("var_0", new double[]{0.3, 0.7});
        par.setLikelyhood("var_0", new double[]{0.3, 0.7});
        seq.globalUpdate();
        par.globalUpdate();
        assertSameMarginals(seq, par, 8);
    }

    @Test
    void parallel_concurrentStress_noDataRaces() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(15);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> par = new BayesInstance<>(jt);
        par.setParallelEnabled(true);

        for (int round = 0; round < 50; round++) {
            double bias = 0.05 * (round % 20);
            forced.setLikelyhood("var_0", new double[]{bias, 1.0 - bias});
            par.setLikelyhood("var_0", new double[]{bias, 1.0 - bias});

            if (round % 3 == 0) {
                forced.setLikelyhood("var_5", new double[]{0.4, 0.6});
                par.setLikelyhood("var_5", new double[]{0.4, 0.6});
            }

            forced.globalUpdateForced();
            par.globalUpdate();

            assertSameMarginals(forced, par, 16);
        }
    }
}

package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static io.quarkmind.inference.bayes.IncrementalUpdateTest.assertSameMarginals;

/**
 * Refs #632 — batch retraction for all-evidence-changes-every-tick pattern.
 */
public class BatchEvidenceTest {

    @Test
    void batchSetEvidence_producesIdenticalPosteriorsToSequential() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(16);
        BayesInstance<?> sequential = new BayesInstance<>(jt);
        BayesInstance<?> batch = new BayesInstance<>(jt);

        sequential.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        sequential.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.6, 0.4});
        sequential.setLikelyhood(BayesNetworkFixtures.varName(8), new double[]{0.3, 0.7});
        sequential.globalUpdate();

        batch.batchSetEvidence(Map.of(
                BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2},
                BayesNetworkFixtures.varName(4), new double[]{0.6, 0.4},
                BayesNetworkFixtures.varName(8), new double[]{0.3, 0.7}
        ));
        batch.globalUpdate();

        assertSameMarginals(sequential, batch, 16);
    }

    @Test
    void batchSetEvidence_replacesAllPriorEvidence() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> batch = new BayesInstance<>(jt);

        // First round
        batch.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        batch.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.6, 0.4});
        batch.globalUpdate();

        // Second round: completely different evidence set
        forced.setLikelyhood(BayesNetworkFixtures.varName(2), new double[]{0.9, 0.1});
        forced.setLikelyhood(BayesNetworkFixtures.varName(6), new double[]{0.3, 0.7});
        forced.globalUpdateForced();

        batch.batchSetEvidence(Map.of(
                BayesNetworkFixtures.varName(2), new double[]{0.9, 0.1},
                BayesNetworkFixtures.varName(6), new double[]{0.3, 0.7}
        ));
        batch.globalUpdate();

        assertSameMarginals(forced, batch, 8);
    }

    @Test
    void batchSetEvidence_emptyMap_unsetsAllEvidence() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> noEvidence = new BayesInstance<>(jt);
        BayesInstance<?> batch = new BayesInstance<>(jt);

        batch.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        batch.globalUpdate();

        noEvidence.globalUpdateForced();
        batch.batchSetEvidence(Map.of());
        batch.globalUpdate();

        assertSameMarginals(noEvidence, batch, 8);
    }

    @Test
    void batchSetEvidence_repeatedCycles_producesCorrectResults() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> batch = new BayesInstance<>(jt);

        double[][][] evidenceSets = {
                {{0.8, 0.2}, {0.6, 0.4}},
                {{0.3, 0.7}, {0.9, 0.1}},
                {{0.5, 0.5}, {0.5, 0.5}},
        };

        for (double[][] ev : evidenceSets) {
            forced.setLikelyhood(BayesNetworkFixtures.varName(0), ev[0]);
            forced.setLikelyhood(BayesNetworkFixtures.varName(4), ev[1]);
            forced.globalUpdateForced();

            batch.batchSetEvidence(Map.of(
                    BayesNetworkFixtures.varName(0), ev[0],
                    BayesNetworkFixtures.varName(4), ev[1]
            ));
            batch.globalUpdate();

            assertSameMarginals(forced, batch, 8);
        }
    }

    @Test
    void batchSetEvidence_statsReflectDirtyCount() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.batchSetEvidence(Map.of(
                BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2},
                BayesNetworkFixtures.varName(4), new double[]{0.6, 0.4},
                BayesNetworkFixtures.varName(7), new double[]{0.3, 0.7}
        ));
        instance.globalUpdate();

        assertThat(instance.getStats().evidenceVariableCount()).isEqualTo(3);
        assertThat(instance.getStats().dirtyVariableCount()).isEqualTo(3);
    }
}

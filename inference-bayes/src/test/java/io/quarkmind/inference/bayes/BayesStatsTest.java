package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refs #633 — observability stats for runtime tuning.
 */
public class BayesStatsTest {

    @Test
    void stats_reflectCliqueTopology() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);
        BayesStats stats = instance.getStats();

        assertThat(stats.totalCliqueCount()).isGreaterThan(0);
        assertThat(stats.maxCliqueSize()).isGreaterThanOrEqualTo(2);
        assertThat(stats.evidenceVariableCount()).isEqualTo(0);
        assertThat(stats.dirtyVariableCount()).isEqualTo(0);
        assertThat(stats.retractionCount()).isEqualTo(0);
    }

    @Test
    void stats_trackEvidenceCount() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        instance.setLikelyhood(BayesNetworkFixtures.varName(3), new double[]{0.6, 0.4});
        instance.globalUpdate();

        assertThat(instance.getStats().evidenceVariableCount()).isEqualTo(2);
    }

    @Test
    void stats_trackDirtyCountAfterPropagation() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        instance.globalUpdate();
        assertThat(instance.getStats().dirtyVariableCount()).isEqualTo(1);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.3, 0.7});
        instance.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.6, 0.4});
        instance.globalUpdate();
        assertThat(instance.getStats().dirtyVariableCount()).isEqualTo(2);
    }

    @Test
    void stats_trackPropagationTime() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        instance.globalUpdate();

        assertThat(instance.getStats().lastPropagationNanos()).isGreaterThan(0);
    }

    @Test
    void stats_trackRetractionCount() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt, true);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        instance.globalUpdate();
        assertThat(instance.getStats().retractionCount()).isEqualTo(0);

        // soft→soft change triggers retraction path
        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.3, 0.7});
        instance.globalUpdate();
        assertThat(instance.getStats().retractionCount()).isEqualTo(1);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        instance.globalUpdate();
        assertThat(instance.getStats().retractionCount()).isEqualTo(2);
    }

    @Test
    void stats_noOpUpdateDoesNotChangeTiming() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        instance.globalUpdate();
        long firstNanos = instance.getStats().lastPropagationNanos();

        instance.globalUpdate(); // no-op — Phase 1 early return
        assertThat(instance.getStats().lastPropagationNanos()).isEqualTo(firstNanos);
    }

    @Test
    void stats_unsetEvidenceReducesCount() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        instance.setLikelyhood(BayesNetworkFixtures.varName(3), new double[]{0.6, 0.4});
        instance.globalUpdate();
        assertThat(instance.getStats().evidenceVariableCount()).isEqualTo(2);

        instance.unsetLikelyhood(BayesNetworkFixtures.varName(0));
        instance.globalUpdate();
        assertThat(instance.getStats().evidenceVariableCount()).isEqualTo(1);
    }
}

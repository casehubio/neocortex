package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static io.quarkmind.inference.bayes.IncrementalUpdateTest.assertSameMarginals;

/**
 * Refs #628 — hard evidence support.
 */
public class HardEvidenceTest {

    @Test
    void hardEvidence_producesSamePosteriorsAsEquivalentSoftEvidence() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> soft = new BayesInstance<>(jt);
        BayesInstance<?> hard = new BayesInstance<>(jt);

        soft.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{1.0, 0.0});
        hard.setHardEvidence(BayesNetworkFixtures.varName(0), "false");

        soft.globalUpdate();
        hard.globalUpdate();

        assertSameMarginals(soft, hard, 8);
    }

    @Test
    void hardEvidence_trueState_producesSamePosteriorsAsEquivalentSoftEvidence() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> soft = new BayesInstance<>(jt);
        BayesInstance<?> hard = new BayesInstance<>(jt);

        soft.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.0, 1.0});
        hard.setHardEvidence(BayesNetworkFixtures.varName(0), "true");

        soft.globalUpdate();
        hard.globalUpdate();

        assertSameMarginals(soft, hard, 8);
    }

    @Test
    void hardEvidence_byStateIndex_producesSamePosteriorsAsByName() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> byName = new BayesInstance<>(jt);
        BayesInstance<?> byIdx = new BayesInstance<>(jt);

        byName.setHardEvidence(BayesNetworkFixtures.varName(0), "true");
        BayesVariable var0 = byIdx.getVariables().get(BayesNetworkFixtures.varName(0));
        byIdx.setHardEvidence(var0, 1);

        byName.globalUpdate();
        byIdx.globalUpdate();

        assertSameMarginals(byName, byIdx, 8);
    }

    @Test
    void hardEvidence_canBeRetractedBackToSoftEvidence() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        // Set hard evidence
        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{1.0, 0.0});
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "false");
        forced.globalUpdateForced();
        instance.globalUpdate();
        assertSameMarginals(forced, instance, 8);

        // Retract to soft evidence
        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        instance.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.6, 0.4});
        forced.globalUpdateForced();
        instance.globalUpdate();
        assertSameMarginals(forced, instance, 8);
    }

    @Test
    void hardEvidence_multipleVariables_correctPosteriors() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> forced = new BayesInstance<>(jt);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        forced.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{1.0, 0.0});
        forced.setLikelyhood(BayesNetworkFixtures.varName(4), new double[]{0.0, 1.0});
        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "false");
        instance.setHardEvidence(BayesNetworkFixtures.varName(4), "true");

        forced.globalUpdateForced();
        instance.globalUpdate();

        assertSameMarginals(forced, instance, 8);
    }

    @Test
    void hardEvidence_clampsMarginalsToExpectedState() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setHardEvidence(BayesNetworkFixtures.varName(0), "true");
        instance.globalUpdate();

        double[] dist = instance.marginalize(BayesNetworkFixtures.varName(0)).getDistribution();
        assertThat(dist[0]).as("false state").isCloseTo(0.0, within(1e-10));
        assertThat(dist[1]).as("true state").isCloseTo(1.0, within(1e-10));
    }

    @Test
    void hardEvidence_invalidState_throwsIllegalArgument() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.setHardEvidence(BayesNetworkFixtures.varName(0), "maybe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maybe");
    }

    @Test
    void hardEvidence_invalidVariable_throwsIllegalArgument() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.setHardEvidence("nonexistent", "true"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void hardEvidence_invalidStateIndex_throwsIndexOutOfBounds() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);
        BayesVariable var0 = instance.getVariables().get(BayesNetworkFixtures.varName(0));

        assertThatThrownBy(() -> instance.setHardEvidence(var0, 5))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}

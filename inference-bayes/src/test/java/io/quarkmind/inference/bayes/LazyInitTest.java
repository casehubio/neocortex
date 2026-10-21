package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static io.quarkmind.inference.bayes.IncrementalUpdateTest.assertSameMarginals;

/**
 * Refs #626 — lazy clique state instantiation.
 */
public class LazyInitTest {

    @Test
    void lazyInit_defersStateAllocation() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> lazy = new BayesInstance<>(jt, false, true);

        assertThat(lazy.getCliqueStates()).isNull();
        assertThat(lazy.getSeparatorStates()).isNull();

        lazy.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        lazy.globalUpdate();

        assertThat(lazy.getCliqueStates()).isNotNull();
    }

    @Test
    void lazyInit_producesIdenticalMarginals() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> eager = new BayesInstance<>(jt);
        BayesInstance<?> lazy = new BayesInstance<>(jt, false, true);

        eager.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        lazy.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});

        eager.globalUpdate();
        lazy.globalUpdate();

        assertSameMarginals(eager, lazy, 8);
    }

    @Test
    void lazyInit_withFastRetract_producesIdenticalMarginals() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> eager = new BayesInstance<>(jt, true);
        BayesInstance<?> lazy = new BayesInstance<>(jt, true, true);

        eager.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        lazy.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});

        eager.globalUpdate();
        lazy.globalUpdate();

        assertSameMarginals(eager, lazy, 8);

        // Evidence change triggers retraction
        eager.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.3, 0.7});
        lazy.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.3, 0.7});

        eager.globalUpdate();
        lazy.globalUpdate();

        assertSameMarginals(eager, lazy, 8);
    }

    @Test
    void lazyInit_mapInference_triggersInitialization() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> lazy = new BayesInstance<>(jt, false, true);

        MapAssignment map = lazy.mapInference();

        assertThat(map.assignment()).hasSize(4);
        assertThat(map.assignment()).containsEntry("var_0", "false");
    }

    @Test
    void lazyInit_marginalize_triggersInitialization() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> lazy = new BayesInstance<>(jt, false, true);

        lazy.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        lazy.globalUpdate();

        BayesVariableState state = lazy.marginalize(BayesNetworkFixtures.varName(0));
        assertThat(state.getDistribution()).isNotNull();
    }

    @Test
    void lazyInit_noMemoryForUnusedInstance() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(8);
        BayesInstance<?> lazy = new BayesInstance<>(jt, false, true);

        assertThat(lazy.getCliqueStates()).isNull();
        assertThat(lazy.getSeparatorStates()).isNull();
        assertThat(lazy.getVarStates()).isNull();
    }
}

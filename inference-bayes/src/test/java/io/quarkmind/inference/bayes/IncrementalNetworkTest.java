package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Refs #627 — incremental network modification.
 */
public class IncrementalNetworkTest {

    @Test
    void addLeafVariable_posteriorsMatchFullRebuild() {
        JunctionTree jt5 = BayesNetworkFixtures.buildStar(5);
        BayesInstance<?> incremental = new BayesInstance<>(jt5);

        incremental.addLeafVariable("var_6", new String[]{"false", "true"},
                new double[][]{{0.7, 0.3}, {0.4, 0.6}}, "var_0");

        incremental.setLikelyhood("var_0", new double[]{0.3, 0.7});
        incremental.setLikelyhood("var_6", new double[]{0.8, 0.2});
        incremental.globalUpdate();

        JunctionTree jt6 = BayesNetworkFixtures.buildStar(6);
        BayesInstance<?> fullBuild = new BayesInstance<>(jt6);
        fullBuild.setLikelyhood("var_0", new double[]{0.3, 0.7});
        fullBuild.setLikelyhood("var_6", new double[]{0.8, 0.2});
        fullBuild.globalUpdate();

        for (int i = 0; i <= 6; i++) {
            String varName = BayesNetworkFixtures.varName(i);
            double[] incDist = incremental.marginalize(varName).getDistribution();
            double[] fullDist = fullBuild.marginalize(varName).getDistribution();
            for (int j = 0; j < incDist.length; j++) {
                assertThat(incDist[j]).as("%s[%d]", varName, j)
                        .isCloseTo(fullDist[j], within(1e-10));
            }
        }
    }

    @Test
    void addLeafVariable_preservesExistingEvidence() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(3);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("var_1", new double[]{0.9, 0.1});
        instance.globalUpdate();
        double[] beforeAdd = instance.marginalize("var_0").getDistribution().clone();

        instance.addLeafVariable("var_4", new String[]{"false", "true"},
                new double[][]{{0.7, 0.3}, {0.4, 0.6}}, "var_0");
        instance.globalUpdate();

        double[] afterAdd = instance.marginalize("var_0").getDistribution();
        assertThat(afterAdd[0]).as("Hub marginal shifts slightly with new leaf")
                .isNotCloseTo(beforeAdd[0], within(1e-15));
    }

    @Test
    void addLeafVariable_multipleAdds() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(2);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.addLeafVariable("var_3", new String[]{"false", "true"},
                new double[][]{{0.7, 0.3}, {0.4, 0.6}}, "var_0");
        instance.addLeafVariable("var_4", new String[]{"false", "true"},
                new double[][]{{0.7, 0.3}, {0.4, 0.6}}, "var_0");

        instance.setLikelyhood("var_0", new double[]{0.5, 0.5});
        instance.globalUpdate();

        JunctionTree jtFull = BayesNetworkFixtures.buildStar(4);
        BayesInstance<?> full = new BayesInstance<>(jtFull);
        full.setLikelyhood("var_0", new double[]{0.5, 0.5});
        full.globalUpdate();

        for (int i = 0; i <= 4; i++) {
            String varName = BayesNetworkFixtures.varName(i);
            double[] incDist = instance.marginalize(varName).getDistribution();
            double[] fullDist = full.marginalize(varName).getDistribution();
            for (int j = 0; j < incDist.length; j++) {
                assertThat(incDist[j]).as("%s[%d]", varName, j)
                        .isCloseTo(fullDist[j], within(1e-10));
            }
        }
    }

    @Test
    void removeLeafVariable_posteriorsMatchFullRebuild() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(5);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.removeLeafVariable("var_5");

        instance.setLikelyhood("var_0", new double[]{0.3, 0.7});
        instance.globalUpdate();

        JunctionTree jt4 = BayesNetworkFixtures.buildStar(4);
        BayesInstance<?> full = new BayesInstance<>(jt4);
        full.setLikelyhood("var_0", new double[]{0.3, 0.7});
        full.globalUpdate();

        for (int i = 0; i <= 4; i++) {
            String varName = BayesNetworkFixtures.varName(i);
            double[] incDist = instance.marginalize(varName).getDistribution();
            double[] fullDist = full.marginalize(varName).getDistribution();
            for (int j = 0; j < incDist.length; j++) {
                assertThat(incDist[j]).as("%s[%d]", varName, j)
                        .isCloseTo(fullDist[j], within(1e-10));
            }
        }
    }

    @Test
    void removeLeafVariable_thenAddNew() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(3);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.removeLeafVariable("var_3");
        instance.addLeafVariable("var_new", new String[]{"false", "true"},
                new double[][]{{0.7, 0.3}, {0.4, 0.6}}, "var_0");

        instance.globalUpdate();

        assertThat(instance.getVariables()).containsKey("var_new");
        assertThat(instance.getVariables()).doesNotContainKey("var_3");
        assertThat(instance.marginalize("var_new").getDistribution()).isNotNull();
    }

    @Test
    void addLeafVariable_duplicateName_throws() {
        JunctionTree jt = BayesNetworkFixtures.buildStar(3);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.addLeafVariable("var_1",
                new String[]{"false", "true"}, new double[][]{{0.5, 0.5}}, "var_0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void removeLeafVariable_nonLeaf_throws() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.removeLeafVariable("var_0"))
                .isInstanceOf(IllegalStateException.class);
    }
}

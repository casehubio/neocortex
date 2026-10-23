package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class NetworkSpecBuilderTest {

    @Test
    void toBuilderSimpleNetwork() {
        var smoking = new VariableSpec("Smoking", List.of("yes", "no"), "",
            SpecStatus.PROPOSED, new CptSpec.DirectCpt(new double[][]{{0.3, 0.7}}));
        var cancer = new VariableSpec("Cancer", List.of("present", "absent"), "",
            SpecStatus.PROPOSED, new CptSpec.NoisyOr(
                List.of(new ParentInfluence("Smoking", 0.7)), 0.01));
        var edge = new EdgeSpec("Smoking", "Cancer", "risk factor", SpecStatus.PROPOSED);
        var spec = new NetworkSpec("medical", List.of(smoking, cancer), List.of(edge), "desc");

        BayesNetwork network = spec.toBayesNetwork();
        assertThat(network).isNotNull();
        int count = 0;
        for (var node : network) count++;
        assertThat(count).isEqualTo(2);
    }

    @Test
    void toBuilderExcludesRejectedEdges() {
        var a = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED,
            new CptSpec.DirectCpt(new double[][]{{0.5, 0.5}}));
        var b = new VariableSpec("B", List.of("y", "n"), "", SpecStatus.PROPOSED,
            new CptSpec.DirectCpt(new double[][]{{0.5, 0.5}}));
        var edge = new EdgeSpec("A", "B", "", SpecStatus.REJECTED);
        var spec = new NetworkSpec("test", List.of(a, b), List.of(edge), "desc");

        BayesNetwork network = spec.toBayesNetwork();
        for (var node : network) {
            BayesVariable<?> var = node.getContent();
            if (var.getName().equals("B")) {
                assertThat(var.getGiven()).isEmpty();
            }
        }
    }

    @Test
    void toBuilderExcludesRejectedVariables() {
        var a = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED,
            new CptSpec.DirectCpt(new double[][]{{0.5, 0.5}}));
        var b = new VariableSpec("B", List.of("y", "n"), "", SpecStatus.REJECTED,
            new CptSpec.DirectCpt(new double[][]{{0.5, 0.5}}));
        var spec = new NetworkSpec("test", List.of(a, b), List.of(), "desc");

        BayesNetwork network = spec.toBayesNetwork();
        int count = 0;
        for (var node : network) count++;
        assertThat(count).isEqualTo(1);
    }

    @Test
    void toBuilderUniformCpt() {
        var v = new VariableSpec("X", List.of("a", "b", "c"), "",
            SpecStatus.PROPOSED, new CptSpec.Uniform());
        var spec = new NetworkSpec("test", List.of(v), List.of(), "desc");
        BayesNetwork network = spec.toBayesNetwork();
        for (var node : network) {
            double[][] table = node.getContent().getProbabilityTable();
            for (double val : table[0]) {
                assertThat(val).isCloseTo(1.0 / 3, within(1e-9));
            }
        }
    }

    @Test
    void toBuilderNullCptDefaultsToUniform() {
        var v = new VariableSpec("X", List.of("a", "b"), "",
            SpecStatus.PROPOSED, null);
        var spec = new NetworkSpec("test", List.of(v), List.of(), "desc");
        BayesNetwork network = spec.toBayesNetwork();
        for (var node : network) {
            double[][] table = node.getContent().getProbabilityTable();
            assertThat(table[0][0]).isCloseTo(0.5, within(1e-9));
            assertThat(table[0][1]).isCloseTo(0.5, within(1e-9));
        }
    }

    @Test
    void toBuilderMultiParentNoisyOrRowOrdering() {
        var a = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED,
                                 new CptSpec.DirectCpt(new double[][]{{0.5, 0.5}}));
        var b = new VariableSpec("B", List.of("y", "n"), "", SpecStatus.PROPOSED,
                                 new CptSpec.DirectCpt(new double[][]{{0.5, 0.5}}));
        var c = new VariableSpec("C", List.of("present", "absent"), "", SpecStatus.PROPOSED,
                                 new CptSpec.NoisyOr(
                                         List.of(new ParentInfluence("A", 0.6), new ParentInfluence("B", 0.4)),
                                         0.05));
        var spec = new NetworkSpec("test",
                                   List.of(a, b, c),
                                   List.of(new EdgeSpec("A", "C", "", SpecStatus.PROPOSED),
                                           new EdgeSpec("B", "C", "", SpecStatus.PROPOSED)),
                                   "desc");

        BayesNetwork network = spec.toBayesNetwork();
        for (var node : network) {
            BayesVariable<?> var = node.getContent();
            if (var.getName().equals("C")) {
                assertThat(var.getGiven()).containsExactly("A", "B");
                double[][] table = var.getProbabilityTable();
                assertThat(table).hasNumberOfRows(4);
                // Row 0: A=y(active), B=y(active) → P(present) high
                assertThat(table[0][0]).isGreaterThan(0.5);
                // Row 3: A=n(inactive), B=n(inactive) → P(present) = leak = 0.05
                assertThat(table[3][0]).isCloseTo(0.05, within(1e-9));
            }
        }
    }

    @Test
    void toBayesNetworkCanRunInference() {
        var smoking = new VariableSpec("Smoking", List.of("yes", "no"), "",
                                       SpecStatus.PROPOSED, new CptSpec.DirectCpt(new double[][]{{0.3, 0.7}}));
        var cancer = new VariableSpec("Cancer", List.of("present", "absent"), "",
                                      SpecStatus.PROPOSED, new CptSpec.NoisyOr(
                List.of(new ParentInfluence("Smoking", 0.7)), 0.01));
        var edge = new EdgeSpec("Smoking", "Cancer", "", SpecStatus.PROPOSED);
        var spec = new NetworkSpec("test", List.of(smoking, cancer), List.of(edge), "desc");

        var instance = spec.toBuilder().buildInstance();
        // Set Smoking = yes (first outcome, the "active" state for Noisy-OR)
        instance.setLikelyhood("Smoking", new double[]{1.0, 0.0});
        instance.globalUpdate();
        var      marginal = instance.marginalize("Cancer");
        double[] dist     = marginal.getDistribution();
        // P(present | Smoking=yes) should be high (~0.703 from Noisy-OR: 1 - 0.99*0.3)
        assertThat(dist[0]).isGreaterThan(0.5);
        assertThat(dist[0]).isLessThan(0.9);
    }


    @Test
    void toBuilderEmptySpecThrows() {
        var spec = new NetworkSpec("test", List.of(), List.of(), "desc");
        assertThatThrownBy(spec::toBayesNetwork)
                .isInstanceOf(IllegalStateException.class);
    }

}

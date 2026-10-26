package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CptConversionTest {

    @Test
    void noisyOrSingleParent() {
        var noisy = new CptSpec.NoisyOr(
                List.of(new ParentInfluence("A", 0.8)),
                0.01);
        double[][] cpt = NoisyOrExpander.expand(noisy, 2);
        assertThat(cpt).hasNumberOfRows(2);
        // Row 0: A=first-state (active) → pAbsent = 0.99 * 0.2 = 0.198
        assertThat(cpt[0][0]).isCloseTo(0.802, within(1e-9));   // P(caused | A=active)
        assertThat(cpt[0][1]).isCloseTo(0.198, within(1e-9));   // P(absent | A=active)
        // Row 1: A=second-state (inactive) → pAbsent = 0.99
        assertThat(cpt[1][0]).isCloseTo(0.01, within(1e-9));    // P(caused | A=inactive)
        assertThat(cpt[1][1]).isCloseTo(0.99, within(1e-9));    // P(absent | A=inactive)
    }

    @Test
    void noisyOrTwoParents() {
        var noisy = new CptSpec.NoisyOr(
                List.of(new ParentInfluence("A", 0.6),
                        new ParentInfluence("B", 0.4)),
                0.05);
        double[][] cpt = NoisyOrExpander.expand(noisy, 2);
        assertThat(cpt).hasNumberOfRows(4);
        // Row 0: A=active, B=active → pAbsent = 0.95 * 0.4 * 0.6 = 0.228
        assertThat(cpt[0][0]).isCloseTo(0.772, within(1e-9));   // P(caused)
        assertThat(cpt[0][1]).isCloseTo(0.228, within(1e-9));   // P(absent)
        // Row 1: A=active, B=inactive → pAbsent = 0.95 * 0.4 = 0.38
        assertThat(cpt[1][0]).isCloseTo(0.62, within(1e-9));
        // Row 2: A=inactive, B=active → pAbsent = 0.95 * 0.6 = 0.57
        assertThat(cpt[2][0]).isCloseTo(0.43, within(1e-9));
        // Row 3: A=inactive, B=inactive → pAbsent = 0.95
        assertThat(cpt[3][0]).isCloseTo(0.05, within(1e-9));
    }

    @Test
    void noisyOrRejectsNonBinary() {
        var noisy = new CptSpec.NoisyOr(
                List.of(new ParentInfluence("A", 0.5)),
                0.1);
        assertThatThrownBy(() -> NoisyOrExpander.expand(noisy, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2");
    }

    @Test
    void noisyOrLeakOnly() {
        var        noisy = new CptSpec.NoisyOr(List.of(), 0.1);
        double[][] cpt   = NoisyOrExpander.expand(noisy, 2);
        assertThat(cpt).hasNumberOfRows(1);
        // No parents → pAbsent = 1 - 0.1 = 0.9
        assertThat(cpt[0][0]).isCloseTo(0.1, within(1e-9));   // P(caused) = leak
        assertThat(cpt[0][1]).isCloseTo(0.9, within(1e-9));   // P(absent) = 1-leak
    }

    @Test
    void noisyMaxRecordFieldsAccessible() {
        var noisyMax = new CptSpec.NoisyMax(
                List.of(new NoisyMaxParent("A", new double[]{0.6, 0.2, 0.2})),
                new double[]{0.01, 0.04, 0.95});

        assertThat(noisyMax.parents()).hasSize(1);
        assertThat(noisyMax.parents().get(0).parentName()).isEqualTo("A");
        assertThat(noisyMax.parents().get(0).distribution()).containsExactly(0.6, 0.2, 0.2);
        assertThat(noisyMax.leak()).containsExactly(0.01, 0.04, 0.95);
    }

    @Test
    void noisyMaxSingleParentThreeOutcomes() {
        var noisyMax = new CptSpec.NoisyMax(
                List.of(new NoisyMaxParent("A", new double[]{0.6, 0.3, 0.1})),
                new double[]{0.05, 0.10, 0.85});

        double[][] cpt = NoisyMaxExpander.expand(noisyMax, 3);
        assertThat(cpt).hasNumberOfRows(2);

        // Row 0: A=active
        assertThat(cpt[0][0]).isCloseTo(0.62, within(1e-9));
        assertThat(cpt[0][1]).isCloseTo(0.295, within(1e-9));
        assertThat(cpt[0][2]).isCloseTo(0.085, within(1e-9));

        // Row 1: A=inactive → only leak
        assertThat(cpt[1][0]).isCloseTo(0.05, within(1e-9));
        assertThat(cpt[1][1]).isCloseTo(0.10, within(1e-9));
        assertThat(cpt[1][2]).isCloseTo(0.85, within(1e-9));
    }

    @Test
    void noisyMaxTwoParentsThreeOutcomes() {
        var noisyMax = new CptSpec.NoisyMax(
                List.of(new NoisyMaxParent("A", new double[]{0.6, 0.3, 0.1}),
                        new NoisyMaxParent("B", new double[]{0.4, 0.4, 0.2})),
                new double[]{0.05, 0.10, 0.85});

        double[][] cpt = NoisyMaxExpander.expand(noisyMax, 3);
        assertThat(cpt).hasNumberOfRows(4);

        for (int r = 0; r < 4; r++) {
            double sum = 0;
            for (double v : cpt[r]) {sum += v;}
            assertThat(sum).as("row %d sums to 1", r).isCloseTo(1.0, within(1e-9));
        }

        // Row 0: both active — most severe outcomes most likely
        assertThat(cpt[0][0]).as("P(severe | both active)").isGreaterThan(0.5);
        // Row 3: both inactive — only leak
        assertThat(cpt[3][0]).isCloseTo(0.05, within(1e-9));
        assertThat(cpt[3][1]).isCloseTo(0.10, within(1e-9));
        assertThat(cpt[3][2]).isCloseTo(0.85, within(1e-9));
    }

    @Test
    void noisyMaxReducesToNoisyOrForBinary() {
        var noisyOr = new CptSpec.NoisyOr(
                List.of(new ParentInfluence("A", 0.8)),
                0.01);
        double[][] orCpt = NoisyOrExpander.expand(noisyOr, 2);

        var noisyMax = new CptSpec.NoisyMax(
                List.of(new NoisyMaxParent("A", new double[]{0.8, 0.2})),
                new double[]{0.01, 0.99});
        double[][] maxCpt = NoisyMaxExpander.expand(noisyMax, 2);

        assertThat(maxCpt).hasNumberOfRows(orCpt.length);
        for (int r = 0; r < orCpt.length; r++) {
            for (int c = 0; c < 2; c++) {
                assertThat(maxCpt[r][c]).as("row %d col %d", r, c)
                                        .isCloseTo(orCpt[r][c], within(1e-10));
            }
        }
    }

    @Test
    void noisyMaxLeakOnly() {
        var noisyMax = new CptSpec.NoisyMax(
                List.of(),
                new double[]{0.05, 0.10, 0.85});
        double[][] cpt = NoisyMaxExpander.expand(noisyMax, 3);
        assertThat(cpt).hasNumberOfRows(1);
        assertThat(cpt[0][0]).isCloseTo(0.05, within(1e-9));
        assertThat(cpt[0][1]).isCloseTo(0.10, within(1e-9));
        assertThat(cpt[0][2]).isCloseTo(0.85, within(1e-9));
    }

    @Test
    void noisyMaxDimensionMismatch_throws() {
        var noisyMax = new CptSpec.NoisyMax(
                List.of(new NoisyMaxParent("A", new double[]{0.6, 0.4})),
                new double[]{0.05, 0.10, 0.85});

        assertThatThrownBy(() -> NoisyMaxExpander.expand(noisyMax, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void noisyMaxNonNormalisedDistribution_throws() {
        var noisyMax = new CptSpec.NoisyMax(
                List.of(new NoisyMaxParent("A", new double[]{0.6, 0.3, 0.2})),
                new double[]{0.05, 0.10, 0.85});

        assertThatThrownBy(() -> NoisyMaxExpander.expand(noisyMax, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sums to");
    }


    @Test
    void noisyOrAutoRoutesToNoisyMaxForThreeOutcomes() {
        var noisyOr = new CptSpec.NoisyOr(
                List.of(new ParentInfluence("A", 0.8)),
                0.01);

        double[][] cpt = NoisyOrExpander.expand(noisyOr, 3);
        assertThat(cpt).hasNumberOfRows(2);
        assertThat(cpt[0].length).isEqualTo(3);

        // Row 0: A=active → strength 0.8 → distribution [0.8, 0.0, 0.2], leak [0.01, 0.0, 0.99]
        assertThat(cpt[0][0]).as("P(severe|A active)").isGreaterThan(0.5);

        // Row 1: A=inactive → leak only [0.01, 0.0, 0.99]
        assertThat(cpt[1][0]).isCloseTo(0.01, within(1e-9));
        assertThat(cpt[1][2]).isCloseTo(0.99, within(1e-9));

        for (double[] row : cpt) {
            double sum = 0;
            for (double v : row) {sum += v;}
            assertThat(sum).isCloseTo(1.0, within(1e-9));
        }
    }

    @Test
    void noisyMaxNetworkSpecRoundTrip() {
        var spec = new NetworkSpec("test",
                                   List.of(
                                           new VariableSpec("A", List.of("yes", "no"), "", SpecStatus.ACCEPTED, new CptSpec.Uniform()),
                                           new VariableSpec("C", List.of("severe", "moderate", "absent"), "", SpecStatus.ACCEPTED,
                                                            new CptSpec.NoisyMax(
                                                                    List.of(new NoisyMaxParent("A", new double[]{0.6, 0.3, 0.1})),
                                                                    new double[]{0.05, 0.10, 0.85}))),
                                   List.of(new EdgeSpec("A", "C", "", SpecStatus.ACCEPTED)),
                                   "test domain");

        BayesNetwork     network = spec.toBayesNetwork();
        BayesVariable<?> cVar    = null;
        for (var node : network) {
            if (node.getContent().getName().equals("C")) {
                cVar = node.getContent();
            }
        }
        assertThat(cVar).isNotNull();
        double[][] cpt = cVar.getProbabilityTable();
        assertThat(cpt).hasNumberOfRows(2);
        for (double[] row : cpt) {
            double sum = 0;
            for (double v : row) {sum += v;}
            assertThat(sum).isCloseTo(1.0, within(1e-9));
        }
    }

    @Test
    void noisyMaxJacksonRoundTrip() throws Exception {
        var original = new CptSpec.NoisyMax(
                List.of(new NoisyMaxParent("A", new double[]{0.6, 0.3, 0.1})),
                new double[]{0.05, 0.10, 0.85});

        var     mapper       = new com.fasterxml.jackson.databind.ObjectMapper();
        String  json         = mapper.writeValueAsString(original);
        CptSpec deserialized = mapper.readValue(json, CptSpec.class);

        assertThat(deserialized).isInstanceOf(CptSpec.NoisyMax.class);
        var roundTripped = (CptSpec.NoisyMax) deserialized;
        assertThat(roundTripped.parents()).hasSize(1);
        assertThat(roundTripped.parents().get(0).parentName()).isEqualTo("A");
        assertThat(roundTripped.parents().get(0).distribution()).containsExactly(0.6, 0.3, 0.1);
        assertThat(roundTripped.leak()).containsExactly(0.05, 0.10, 0.85);
    }


}

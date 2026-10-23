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
        assertThat(cpt[0][0]).isCloseTo(0.01, within(1e-9));
        assertThat(cpt[0][1]).isCloseTo(0.99, within(1e-9));
        assertThat(cpt[1][0]).isCloseTo(0.002, within(1e-9));
        assertThat(cpt[1][1]).isCloseTo(0.998, within(1e-9));
    }

    @Test
    void noisyOrTwoParents() {
        var noisy = new CptSpec.NoisyOr(
            List.of(new ParentInfluence("A", 0.6),
                    new ParentInfluence("B", 0.4)),
            0.05);
        double[][] cpt = NoisyOrExpander.expand(noisy, 2);
        assertThat(cpt).hasNumberOfRows(4);
        assertThat(cpt[0][0]).isCloseTo(0.05, within(1e-9));
        assertThat(cpt[1][0]).isCloseTo(0.03, within(1e-9));
        assertThat(cpt[2][0]).isCloseTo(0.02, within(1e-9));
        assertThat(cpt[3][0]).isCloseTo(0.012, within(1e-9));
    }

    @Test
    void noisyOrRejectsNonBinary() {
        var noisy = new CptSpec.NoisyOr(
            List.of(new ParentInfluence("A", 0.5)),
            0.1);
        assertThatThrownBy(() -> NoisyOrExpander.expand(noisy, 3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("binary");
    }

    @Test
    void noisyOrLeakOnly() {
        var noisy = new CptSpec.NoisyOr(List.of(), 0.1);
        double[][] cpt = NoisyOrExpander.expand(noisy, 2);
        assertThat(cpt).hasNumberOfRows(1);
        assertThat(cpt[0][0]).isCloseTo(0.1, within(1e-9));
        assertThat(cpt[0][1]).isCloseTo(0.9, within(1e-9));
    }
}

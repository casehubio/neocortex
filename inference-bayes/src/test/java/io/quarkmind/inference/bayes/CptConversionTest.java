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
        assertThatThrownBy(() -> NoisyOrExpander.expand(noisy, 3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("binary");
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
}

package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PadDtwTest {

    @Test
    void identicalSeriesHasZeroCost() {
        double[][] a = {{0.1, 0.2, 0.3}, {0.2, 0.3, 0.4}, {0.3, 0.4, 0.5}};
        double[][] b = {{0.1, 0.2, 0.3}, {0.2, 0.3, 0.4}, {0.3, 0.4, 0.5}};
        PadDtw.DtwResult result = PadDtw.compute(a, b);
        assertThat(result.normalizedCost()).isCloseTo(0.0, within(0.001));
        assertThat(result.similarity()).isCloseTo(1.0, within(0.001));
        assertThat(result.alignment()).hasSize(3);
    }

    @Test
    void oppositeSeriesHasHighCost() {
        double[][] a = {{-1.0, -1.0, -1.0}, {0.0, 0.0, 0.0}, {1.0, 1.0, 1.0}};
        double[][] b = {{1.0, 1.0, 1.0}, {0.0, 0.0, 0.0}, {-1.0, -1.0, -1.0}};
        PadDtw.DtwResult result = PadDtw.compute(a, b);
        assertThat(result.normalizedCost()).isGreaterThan(0.5);
        assertThat(result.similarity()).isLessThan(0.7);
    }

    @Test
    void unequalLengthSeriesProducesAlignment() {
        double[][] a = {{0.1, 0.2, 0.3}, {0.2, 0.3, 0.4}};
        double[][] b = {{0.1, 0.2, 0.3}, {0.15, 0.25, 0.35}, {0.2, 0.3, 0.4}};
        PadDtw.DtwResult result = PadDtw.compute(a, b);
        assertThat(result.similarity()).isGreaterThan(0.5);
        assertThat(result.alignment()).isNotEmpty();
    }

    @Test
    void emptySeriesReturnsMaxCost() {
        PadDtw.DtwResult result = PadDtw.compute(new double[0][], new double[][]{{0.1, 0.2, 0.3}});
        assertThat(result.similarity()).isCloseTo(0.0, within(0.001));
        assertThat(result.alignment()).isEmpty();
    }

    @Test
    void singlePointSeriesProducesOneAlignment() {
        double[][] a = {{0.5, 0.5, 0.5}};
        double[][] b = {{0.5, 0.5, 0.5}};
        PadDtw.DtwResult result = PadDtw.compute(a, b);
        assertThat(result.normalizedCost()).isCloseTo(0.0, within(0.001));
        assertThat(result.alignment()).hasSize(1);
        assertThat(result.alignment().getFirst().indexA()).isEqualTo(0);
        assertThat(result.alignment().getFirst().indexB()).isEqualTo(0);
    }
}
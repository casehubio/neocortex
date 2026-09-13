package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.cbr.WarpingConstraint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void domainCorrelationAcceptsPValue() {
        var dc = new DomainCorrelation(0.8, List.of(), 10,
                     CorrelationStrength.STRONG, 0.01, 0, 0);
        assertEquals(0.01, dc.pValue(), 1e-9);
    }

    @Test
    void domainCorrelationAcceptsAttributionCounts() {
        var dc = new DomainCorrelation(0.8, List.of(), 10,
                     CorrelationStrength.STRONG, 0.01, 5, 8);
        assertEquals(5, dc.contextAttributedCount());
        assertEquals(8, dc.totalMoodCount());
    }

    @Test
    void fromSimilarityWithSignificance_strongAndSignificant() {
        assertEquals(CorrelationStrength.STRONG,
                     CorrelationStrength.fromSimilarity(0.8, 0.01));
    }

    @Test
    void fromSimilarityWithSignificance_strongButInsignificant_downgraded() {
        assertEquals(CorrelationStrength.WEAK,
                     CorrelationStrength.fromSimilarity(0.8, 0.10));
    }

    @Test
    void fromSimilarityWithSignificance_moderateAndSignificant() {
        assertEquals(CorrelationStrength.MODERATE,
                     CorrelationStrength.fromSimilarity(0.5, 0.03));
    }

    @Test
    void fromSimilarityWithSignificance_nanPValue_noDowngrade() {
        assertEquals(CorrelationStrength.STRONG,
                     CorrelationStrength.fromSimilarity(0.8, Double.NaN));
    }

    @Test
    void computeWithUnconstrainedMatchesDefault() {
        double[][] a = {{0.1, 0.2, 0.3}, {0.4, 0.5, 0.6}, {0.7, 0.8, 0.9}};
        double[][] b = {{0.1, 0.2, 0.3}, {0.5, 0.5, 0.5}, {0.8, 0.9, 1.0}};
        var unconstrained = PadDtw.compute(a, b, new WarpingConstraint.Unconstrained());
        var defaultResult = PadDtw.compute(a, b);
        assertEquals(defaultResult.similarity(), unconstrained.similarity(), 1e-9);
    }

    @Test
    void computeWithSakoeChibaReducesSimilarityForLargeOffset() {
        double[][] a = new double[20][3];
        double[][] b = new double[20][3];
        for (int i = 0; i < 20; i++) {
            a[i] = new double[]{i * 0.05, 0.0, 0.0};
            int shifted = (i + 10) % 20;
            b[i] = new double[]{shifted * 0.05, 0.0, 0.0};
        }
        var wide = PadDtw.compute(a, b, new WarpingConstraint.SakoeChibaBand(15));
        var narrow = PadDtw.compute(a, b, new WarpingConstraint.SakoeChibaBand(3));
        assertTrue(wide.similarity() > narrow.similarity(),
                   "narrow band should reduce similarity for large offset");
    }

    @Test
    void computeWithNullConstraintIsUnconstrained() {
        double[][] a = {{0.1, 0.2, 0.3}, {0.4, 0.5, 0.6}};
        double[][] b = {{0.2, 0.3, 0.4}, {0.5, 0.6, 0.7}};
        var result = PadDtw.compute(a, b, null);
        var defaultResult = PadDtw.compute(a, b);
        assertEquals(defaultResult.similarity(), result.similarity(), 1e-9);
    }

    @Test
    void significanceTest_correlatedSeries_lowPValue() {
        double[][] a = new double[30][3];
        double[][] b = new double[30][3];
        for (int i = 0; i < 30; i++) {
            double v = i * 0.03;
            a[i] = new double[]{v, v * 0.5, v * 0.3};
            b[i] = new double[]{v + 0.01, v * 0.5 + 0.01, v * 0.3 + 0.01};
        }
        var result = PadDtw.significanceTest(a, b, null, 200, 42L);
        assertTrue(result.pValue() < 0.05, "correlated series should have p < 0.05, got " + result.pValue());
        assertNotEquals(CorrelationStrength.WEAK, result.strength());
    }

    @Test
    void significanceTest_randomSeries_highPValue() {
        var rng = new java.util.Random(123);
        double[][] a = new double[30][3];
        double[][] b = new double[30][3];
        for (int i = 0; i < 30; i++) {
            a[i] = new double[]{rng.nextDouble(), rng.nextDouble(), rng.nextDouble()};
            b[i] = new double[]{rng.nextDouble(), rng.nextDouble(), rng.nextDouble()};
        }
        var result = PadDtw.significanceTest(a, b, null, 200, 42L);
        assertTrue(result.pValue() >= 0.05, "random series should have p >= 0.05, got " + result.pValue());
    }

    @Test
    void significanceTest_deterministicSeed() {
        double[][] a = {{0.1, 0.2, 0.3}, {0.4, 0.5, 0.6}, {0.7, 0.8, 0.9}};
        double[][] b = {{0.2, 0.3, 0.4}, {0.5, 0.6, 0.7}, {0.8, 0.9, 1.0}};
        var r1 = PadDtw.significanceTest(a, b, null, 50, 99L);
        var r2 = PadDtw.significanceTest(a, b, null, 50, 99L);
        assertEquals(r1.pValue(), r2.pValue(), 1e-9);
    }

    @Test
    void significanceTest_tooShortSeries_returnsNaNPValue() {
        double[][] a = {{0.1, 0.2, 0.3}, {0.4, 0.5, 0.6}};
        double[][] b = {{0.2, 0.3, 0.4}, {0.5, 0.6, 0.7}};
        var result = PadDtw.significanceTest(a, b, null, 200, 42L);
        assertTrue(Double.isNaN(result.pValue()));
    }
}
package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GammaFunctionTest {

    @Test
    void logGamma_knownValues() {
        assertEquals(0.0, GammaFunction.logGamma(1.0), 1e-10);
        assertEquals(0.0, GammaFunction.logGamma(2.0), 1e-10);
        assertEquals(0.5723649429, GammaFunction.logGamma(0.5), 1e-6);
        assertEquals(3.1780538303, GammaFunction.logGamma(5.0), 1e-6);
    }

    @Test
    void regularizedGammaP_zeroX_returnsZero() {
        assertEquals(0.0, GammaFunction.regularizedGammaP(1.0, 0.0), 1e-10);
    }

    @Test
    void regularizedGammaP_largeX_approachesOne() {
        assertTrue(GammaFunction.regularizedGammaP(1.0, 20.0) > 0.999);
    }

    @Test
    void chiSquaredPValue_df1() {
        assertEquals(0.05, GammaFunction.chiSquaredPValue(3.841, 1), 0.001);
    }

    @Test
    void chiSquaredPValue_df2() {
        assertEquals(0.05, GammaFunction.chiSquaredPValue(5.991, 2), 0.001);
    }

    @Test
    void chiSquaredPValue_zeroStatistic_returnsOne() {
        assertEquals(1.0, GammaFunction.chiSquaredPValue(0.0, 1), 1e-10);
    }

    @Test
    void chiSquaredPValue_veryLargeStatistic_nearZero() {
        assertTrue(GammaFunction.chiSquaredPValue(100.0, 1) < 1e-10);
    }

    @Test
    void regularizedGammaP_halfInteger_a() {
        double result = GammaFunction.regularizedGammaP(0.5, 0.5);
        assertTrue(result > 0.5 && result < 0.8);
    }
}

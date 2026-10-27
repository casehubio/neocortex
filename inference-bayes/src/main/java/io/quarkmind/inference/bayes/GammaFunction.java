package io.quarkmind.inference.bayes;

final class GammaFunction {

    private static final int MAX_ITERATIONS = 200;
    private static final double EPSILON = 1e-15;

    private GammaFunction() {}

    static double chiSquaredPValue(double statistic, int degreesOfFreedom) {
        if (statistic <= 0.0) return 1.0;
        if (degreesOfFreedom <= 0) throw new IllegalArgumentException(
            "Degrees of freedom must be positive: " + degreesOfFreedom);
        return 1.0 - regularizedGammaP(degreesOfFreedom / 2.0, statistic / 2.0);
    }

    static double regularizedGammaP(double a, double x) {
        if (x < 0.0 || a <= 0.0) throw new IllegalArgumentException(
            "Requires a > 0 and x >= 0: a=" + a + ", x=" + x);
        if (x == 0.0) return 0.0;
        if (x < a + 1.0) {
            return gammaSeriesP(a, x);
        } else {
            return 1.0 - gammaContinuedFractionQ(a, x);
        }
    }

    static double logGamma(double x) {
        double[] coeff = {
            0.99999999999980993,
            676.5203681218851,
            -1259.1392167224028,
            771.32342877765313,
            -176.61502916214059,
            12.507343278686905,
            -0.13857109526572012,
            9.9843695780195716e-6,
            1.5056327351493116e-7
        };
        if (x < 0.5) {
            return Math.log(Math.PI / Math.sin(Math.PI * x))
                - logGamma(1.0 - x);
        }
        x -= 1.0;
        double a = coeff[0];
        double t = x + 7.5;
        for (int i = 1; i < coeff.length; i++) {
            a += coeff[i] / (x + i);
        }
        return 0.5 * Math.log(2.0 * Math.PI) + (x + 0.5) * Math.log(t) - t
            + Math.log(a);
    }

    private static double gammaSeriesP(double a, double x) {
        double ap = a;
        double sum = 1.0 / a;
        double term = 1.0 / a;
        for (int n = 1; n <= MAX_ITERATIONS; n++) {
            ap += 1.0;
            term *= x / ap;
            sum += term;
            if (Math.abs(term) < Math.abs(sum) * EPSILON) {
                return sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
            }
        }
        return sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
    }

    private static double gammaContinuedFractionQ(double a, double x) {
        double b = x + 1.0 - a;
        double c = 1.0 / EPSILON;
        double d = 1.0 / b;
        double h = d;
        for (int i = 1; i <= MAX_ITERATIONS; i++) {
            double an = -i * (i - a);
            b += 2.0;
            d = an * d + b;
            if (Math.abs(d) < EPSILON) d = EPSILON;
            c = b + an / c;
            if (Math.abs(c) < EPSILON) c = EPSILON;
            d = 1.0 / d;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1.0) < EPSILON) break;
        }
        return Math.exp(-x + a * Math.log(x) - logGamma(a)) * h;
    }
}

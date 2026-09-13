package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.cbr.WarpingConstraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class PadDtw {
    private PadDtw() {}

    record DtwResult(double normalizedCost, double similarity,
                     List<DtwAlignment> alignment) {
    }

    record SignificanceResult(double similarity, double pValue,
                              CorrelationStrength strength) {
    }

    static DtwResult compute(double[][] query, double[][] candidate) {
        int n = query.length, m = candidate.length;
        if (n == 0 || m == 0) {
            return new DtwResult(Double.MAX_VALUE, 0.0, List.of());
        }

        int        dims = query[0].length;
        double[][] cost = new double[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {cost[i][0] = Double.MAX_VALUE;}
        for (int j = 0; j <= m; j++) {cost[0][j] = Double.MAX_VALUE;}
        cost[0][0] = 0;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                double d = euclidean(query[i - 1], candidate[j - 1], dims);
                cost[i][j] = d + Math.min(cost[i - 1][j],
                                          Math.min(cost[i][j - 1], cost[i - 1][j - 1]));
            }
        }

        double normalizedCost = cost[n][m] / Math.max(n, m);

        List<DtwAlignment> alignment = new ArrayList<>();
        int                i         = n, j = m;
        while (i > 0 && j > 0) {
            alignment.add(new DtwAlignment(i - 1, j - 1));
            double diag = cost[i - 1][j - 1];
            double left = cost[i][j - 1];
            double up   = cost[i - 1][j];
            if (diag <= left && diag <= up) {
                i--;
                j--;
            } else if (up <= left) {
                i--;
            } else {
                j--;
            }
        }
        Collections.reverse(alignment);

        double similarity = 1.0 / (1.0 + normalizedCost);
        return new DtwResult(normalizedCost, similarity, alignment);
    }

    static DtwResult compute(double[][] query, double[][] candidate,
                             WarpingConstraint constraint) {
        if (constraint == null) return compute(query, candidate);
        int n = query.length, m = candidate.length;
        if (n == 0 || m == 0) {
            return new DtwResult(Double.MAX_VALUE, 0.0, List.of());
        }

        int dims = query[0].length;
        double[][] cost = new double[n + 1][m + 1];
        for (int i = 0; i <= n; i++) { cost[i][0] = Double.MAX_VALUE; }
        for (int j = 0; j <= m; j++) { cost[0][j] = Double.MAX_VALUE; }
        cost[0][0] = 0;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (!isWithinConstraint(i - 1, j - 1, n, m, constraint)) {
                    cost[i][j] = Double.MAX_VALUE;
                    continue;
                }
                double d = euclidean(query[i - 1], candidate[j - 1], dims);
                cost[i][j] = d + Math.min(cost[i - 1][j],
                                  Math.min(cost[i][j - 1], cost[i - 1][j - 1]));
            }
        }

        if (cost[n][m] == Double.MAX_VALUE) {
            return new DtwResult(Double.MAX_VALUE, 0.0, List.of());
        }

        double normalizedCost = cost[n][m] / Math.max(n, m);

        List<DtwAlignment> alignment = new ArrayList<>();
        int i = n, j = m;
        while (i > 0 && j > 0) {
            alignment.add(new DtwAlignment(i - 1, j - 1));
            double diag = cost[i - 1][j - 1];
            double left = cost[i][j - 1];
            double up   = cost[i - 1][j];
            if (diag <= left && diag <= up) { i--; j--; }
            else if (up <= left) { i--; }
            else { j--; }
        }
        Collections.reverse(alignment);

        double similarity = 1.0 / (1.0 + normalizedCost);
        return new DtwResult(normalizedCost, similarity, alignment);
    }

    static SignificanceResult significanceTest(double[][] query, double[][] candidate,
                                              WarpingConstraint constraint,
                                              int surrogates, long seed) {
        DtwResult observed = constraint != null
                             ? compute(query, candidate, constraint)
                             : compute(query, candidate);

        int n = candidate.length;
        if (n < 3) {
            return new SignificanceResult(observed.similarity(), Double.NaN,
                       CorrelationStrength.fromSimilarity(observed.similarity()));
        }

        var rng = new Random(seed);
        int atLeastAsGood = 0;
        for (int s = 0; s < surrogates; s++) {
            int shift = 1 + rng.nextInt(n - 1);
            double[][] shifted = new double[n][];
            for (int idx = 0; idx < n; idx++) {
                shifted[idx] = candidate[(idx + shift) % n];
            }
            DtwResult surrogate = constraint != null
                                  ? compute(query, shifted, constraint)
                                  : compute(query, shifted);
            if (surrogate.similarity() >= observed.similarity()) {
                atLeastAsGood++;
            }
        }

        double pValue = (double) atLeastAsGood / surrogates;
        return new SignificanceResult(observed.similarity(), pValue,
                   CorrelationStrength.fromSimilarity(observed.similarity(), pValue));
    }

    private static boolean isWithinConstraint(int i, int j, int n, int m,
                                              WarpingConstraint constraint) {
        return switch (constraint) {
            case WarpingConstraint.Unconstrained u -> true;
            case WarpingConstraint.SakoeChibaBand band -> {
                double scaledI = (double) i * m / n;
                yield Math.abs(scaledI - j) <= band.windowSize();
            }
            case WarpingConstraint.ItakuraParallelogram para -> {
                double maxSlope = para.maxSlope();
                double minSlope = 1.0 / maxSlope;
                double jLow  = minSlope * i;
                double jHigh = maxSlope * i + (m - 1) - maxSlope * (n - 1);
                yield j >= Math.ceil(jLow) && j <= Math.floor(jHigh);
            }
        };
    }

    private static double euclidean(double[] a, double[] b, int dims) {
        double sum = 0;
        for (int d = 0; d < dims; d++) {
            double diff = a[d] - b[d];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
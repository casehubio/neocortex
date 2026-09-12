package io.casehub.neocortex.cognitive.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PadDtw {
    private PadDtw() {}

    record DtwResult(double normalizedCost, double similarity,
                     List<DtwAlignment> alignment) {
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

    private static double euclidean(double[] a, double[] b, int dims) {
        double sum = 0;
        for (int d = 0; d < dims; d++) {
            double diff = a[d] - b[d];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
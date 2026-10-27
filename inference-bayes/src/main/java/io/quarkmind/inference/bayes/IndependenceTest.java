package io.quarkmind.inference.bayes;

import java.util.List;
import java.util.Map;
import java.util.Set;

@FunctionalInterface
public interface IndependenceTest {

    IndependenceResult test(String x, String y, Set<String> conditioningSet,
                            List<Map<String, String>> data);

    record IndependenceResult(boolean independent, double pValue, double statistic) {}

    static IndependenceTest gTest(double alpha) {
        return (x, y, cond, data) -> {
            var tables = ContingencyTable.build(x, y, cond, data);
            if (tables.isEmpty()) return new IndependenceResult(true, 1.0, 0.0);

            double totalG = 0.0;
            int totalDf = 0;
            for (var table : tables) {
                if (table.total() == 0) continue;
                totalG += computeGStatistic(table);
                totalDf += (table.counts().length - 1) * (table.counts()[0].length - 1);
            }
            if (totalDf <= 0) return new IndependenceResult(true, 1.0, 0.0);
            double pValue = GammaFunction.chiSquaredPValue(totalG, totalDf);
            return new IndependenceResult(pValue >= alpha, pValue, totalG);
        };
    }

    static IndependenceTest fisherExact(double alpha) {
        return (x, y, cond, data) -> {
            var tables = ContingencyTable.build(x, y, cond, data);
            if (tables.isEmpty()) return new IndependenceResult(true, 1.0, 0.0);

            double combinedP = 1.0;
            for (var table : tables) {
                if (table.total() == 0) continue;
                int rows = table.counts().length;
                int cols = table.counts()[0].length;
                if (rows > 5 || cols > 5) {
                    double g = computeGStatistic(table);
                    int df = (rows - 1) * (cols - 1);
                    combinedP = Math.min(combinedP,
                        GammaFunction.chiSquaredPValue(g, df));
                } else if (rows == 2 && cols == 2) {
                    combinedP = Math.min(combinedP,
                        fisher2x2(table.counts()));
                } else {
                    combinedP = Math.min(combinedP,
                        fisherRxC(table.counts(), table.rowTotals(),
                            table.colTotals(), table.total()));
                }
            }
            return new IndependenceResult(combinedP >= alpha, combinedP, 0.0);
        };
    }

    static IndependenceTest adaptive(double alpha) {
        return (x, y, cond, data) -> {
            var tables = ContingencyTable.build(x, y, cond, data);
            if (tables.isEmpty()) return new IndependenceResult(true, 1.0, 0.0);

            double totalG = 0.0;
            int totalDf = 0;
            double minFisherP = 1.0;
            boolean usedFisher = false;

            for (var table : tables) {
                if (table.total() == 0) continue;
                if (hasSmallExpectedCounts(table)) {
                    usedFisher = true;
                    int rows = table.counts().length;
                    int cols = table.counts()[0].length;
                    double p;
                    if (rows > 5 || cols > 5) {
                        double g = computeGStatistic(table);
                        int df = (rows - 1) * (cols - 1);
                        p = GammaFunction.chiSquaredPValue(g, df);
                    } else if (rows == 2 && cols == 2) {
                        p = fisher2x2(table.counts());
                    } else {
                        p = fisherRxC(table.counts(), table.rowTotals(),
                            table.colTotals(), table.total());
                    }
                    minFisherP = Math.min(minFisherP, p);
                } else {
                    totalG += computeGStatistic(table);
                    totalDf += (table.counts().length - 1)
                        * (table.counts()[0].length - 1);
                }
            }

            if (usedFisher && totalDf == 0) {
                return new IndependenceResult(minFisherP >= alpha, minFisherP, 0.0);
            }
            if (!usedFisher && totalDf > 0) {
                double pValue = GammaFunction.chiSquaredPValue(totalG, totalDf);
                return new IndependenceResult(pValue >= alpha, pValue, totalG);
            }
            double gP = totalDf > 0
                ? GammaFunction.chiSquaredPValue(totalG, totalDf) : 1.0;
            double combinedP = Math.min(gP, minFisherP);
            return new IndependenceResult(combinedP >= alpha, combinedP, totalG);
        };
    }

    private static double computeGStatistic(ContingencyTable table) {
        double g = 0.0;
        int[][] counts = table.counts();
        int[] rowTotals = table.rowTotals();
        int[] colTotals = table.colTotals();
        int total = table.total();

        for (int i = 0; i < counts.length; i++) {
            for (int j = 0; j < counts[0].length; j++) {
                int observed = counts[i][j];
                if (observed == 0) continue;
                double expected = (double) rowTotals[i] * colTotals[j] / total;
                if (expected == 0.0) continue;
                g += observed * Math.log((double) observed / expected);
            }
        }
        return 2.0 * g;
    }

    private static boolean hasSmallExpectedCounts(ContingencyTable table) {
        int[] rowTotals = table.rowTotals();
        int[] colTotals = table.colTotals();
        int total = table.total();
        for (int rt : rowTotals) {
            for (int ct : colTotals) {
                double expected = (double) rt * ct / total;
                if (expected < 5.0) return true;
            }
        }
        return false;
    }

    private static double fisher2x2(int[][] counts) {
        int a = counts[0][0], b = counts[0][1];
        int c = counts[1][0], d = counts[1][1];
        int n = a + b + c + d;
        int r1 = a + b;
        int c1 = a + c;

        double observedP = hypergeometricProbability(a, r1, c1, n);
        double pValue = 0.0;

        for (int x = Math.max(0, r1 + c1 - n); x <= Math.min(r1, c1); x++) {
            double p = hypergeometricProbability(x, r1, c1, n);
            if (p <= observedP * (1.0 + 1e-7)) {
                pValue += p;
            }
        }
        return Math.min(pValue, 1.0);
    }

    private static double hypergeometricProbability(int k, int n1, int n2, int n) {
        return Math.exp(
            logBinomial(n1, k) + logBinomial(n - n1, n2 - k)
            - logBinomial(n, n2));
    }

    private static double logBinomial(int n, int k) {
        if (k < 0 || k > n) return Double.NEGATIVE_INFINITY;
        return GammaFunction.logGamma(n + 1)
            - GammaFunction.logGamma(k + 1)
            - GammaFunction.logGamma(n - k + 1);
    }

    private static double fisherRxC(int[][] counts, int[] rowTotals,
                                     int[] colTotals, int total) {
        double observedLogP = tableLogProbability(counts, rowTotals, colTotals, total);
        double[] pValueHolder = {0.0};
        enumerateTables(counts, rowTotals, colTotals, total,
            observedLogP, pValueHolder, 0, new int[counts.length][counts[0].length]);
        return Math.min(pValueHolder[0], 1.0);
    }

    private static double tableLogProbability(int[][] counts, int[] rowTotals,
                                               int[] colTotals, int total) {
        double logP = 0.0;
        for (int rt : rowTotals) logP += GammaFunction.logGamma(rt + 1);
        for (int ct : colTotals) logP += GammaFunction.logGamma(ct + 1);
        logP -= GammaFunction.logGamma(total + 1);
        for (int[] row : counts) {
            for (int cell : row) {
                logP -= GammaFunction.logGamma(cell + 1);
            }
        }
        return logP;
    }

    private static void enumerateTables(int[][] observed, int[] rowTotals,
                                         int[] colTotals, int total,
                                         double observedLogP, double[] pValue,
                                         int row, int[][] current) {
        int rows = observed.length;
        int cols = observed[0].length;
        if (row == rows - 1) {
            int[] remaining = colTotals.clone();
            for (int i = 0; i < row; i++) {
                for (int j = 0; j < cols; j++) remaining[j] -= current[i][j];
            }
            for (int j = 0; j < cols; j++) {
                if (remaining[j] < 0) return;
                current[row][j] = remaining[j];
            }
            double logP = tableLogProbability(current, rowTotals, colTotals, total);
            if (logP <= observedLogP + 1e-10) {
                pValue[0] += Math.exp(logP);
            }
            return;
        }

        int[] colRemaining = colTotals.clone();
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < cols; j++) colRemaining[j] -= current[i][j];
        }
        enumerateRow(observed, rowTotals, colTotals, total,
            observedLogP, pValue, row, current, colRemaining, 0, rowTotals[row]);
    }

    private static void enumerateRow(int[][] observed, int[] rowTotals,
                                      int[] colTotals, int total,
                                      double observedLogP, double[] pValue,
                                      int row, int[][] current,
                                      int[] colRemaining, int col, int rowLeft) {
        int cols = observed[0].length;
        if (col == cols - 1) {
            if (rowLeft > colRemaining[col]) return;
            current[row][col] = rowLeft;
            enumerateTables(observed, rowTotals, colTotals, total,
                observedLogP, pValue, row + 1, current);
            return;
        }
        int maxVal = Math.min(rowLeft, colRemaining[col]);
        for (int v = 0; v <= maxVal; v++) {
            current[row][col] = v;
            enumerateRow(observed, rowTotals, colTotals, total,
                observedLogP, pValue, row, current, colRemaining, col + 1, rowLeft - v);
        }
    }
}

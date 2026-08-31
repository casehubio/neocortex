package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.Memory;

import java.time.Instant;
import java.util.List;

/**
 * Computes affect trajectory metrics from a series of {@code domain="affect"}
 * memory entries. Pure static utility — stateless derived computation over
 * store data, same category as {@code MindMapAnalyzer} and
 * {@code RetrievalAnalyzer}.
 *
 * <p>Input memories should be sorted by {@link Memory#createdAt()} ascending.
 * Null PAD values are treated as 0.0.
 */
public final class AffectTrajectoryAnalyzer {

    private static final double SLOPE_THRESHOLD = 1e-9;

    private AffectTrajectoryAnalyzer() {}

    public static AffectTrajectory analyze(List<Memory> affectMemories) {
        int n = affectMemories.size();
        if (n == 0) {
            return new AffectTrajectory(0, 0, 0, TrendDirection.STABLE, 0, 0);
        }
        if (n == 1) {
            return new AffectTrajectory(0, 0, 0, TrendDirection.STABLE, 0, 1);
        }

        double[] times = new double[n];
        double[] pleasures = new double[n];
        double[] arousals = new double[n];
        double[] dominances = new double[n];

        Instant t0 = affectMemories.getFirst().createdAt();
        for (int i = 0; i < n; i++) {
            Memory m = affectMemories.get(i);
            times[i] = m.createdAt() != null
                ? (m.createdAt().getEpochSecond() - t0.getEpochSecond()) / 3600.0
                : 0.0;
            pleasures[i] = m.pleasure() != null ? m.pleasure() : 0.0;
            arousals[i] = m.arousal() != null ? m.arousal() : 0.0;
            dominances[i] = m.dominance() != null ? m.dominance() : 0.0;
        }

        double pleasureSlope = slope(times, pleasures, n);
        double dominanceSlope = slope(times, dominances, n);
        double arousalVolatility = stddev(arousals, n);

        TrendDirection trend;
        if (pleasureSlope > SLOPE_THRESHOLD) {
            trend = TrendDirection.IMPROVING;
        } else if (pleasureSlope < -SLOPE_THRESHOLD) {
            trend = TrendDirection.WORSENING;
        } else {
            trend = TrendDirection.STABLE;
        }

        return new AffectTrajectory(pleasureSlope, arousalVolatility, dominanceSlope,
                                    trend, Math.abs(pleasureSlope), n);
    }

    private static double slope(double[] x, double[] y, int n) {
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
        }
        double denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1e-15) return 0.0;
        return (n * sumXY - sumX * sumY) / denom;
    }

    private static double stddev(double[] values, int n) {
        double sum = 0;
        for (int i = 0; i < n; i++) sum += values[i];
        double mean = sum / n;
        double variance = 0;
        for (int i = 0; i < n; i++) {
            double diff = values[i] - mean;
            variance += diff * diff;
        }
        return Math.sqrt(variance / n);
    }
}

package io.quarkmind.inference.bayes;

/**
 * Log-space arithmetic utilities for numerically stable potential operations.
 * All potentials are stored as log-probabilities; this class provides
 * operations that work directly in log-space without converting back.
 */
public final class LogSpace {

    static final double LOG_ZERO = Double.NEGATIVE_INFINITY;

    private LogSpace() {}

    /**
     * Numerically stable log(sum(exp(values))).
     * Handles -Infinity values (log(0)) correctly.
     */
    static double logsumexp(double[] logValues, int length) {
        double max = LOG_ZERO;
        for (int i = 0; i < length; i++) {
            if (logValues[i] > max) max = logValues[i];
        }
        if (max == LOG_ZERO) return LOG_ZERO;

        double sum = 0.0;
        for (int i = 0; i < length; i++) {
            if (logValues[i] != LOG_ZERO) {
                sum += Math.exp(logValues[i] - max);
            }
        }
        return max + Math.log(sum);
    }

    /**
     * In-place log-space normalization: subtract logsumexp from all entries
     * so they sum to 1 when exponentiated.
     */
    static void normalize(double[] logPotentials) {
        double logSum = logsumexp(logPotentials, logPotentials.length);
        if (logSum == LOG_ZERO) return;
        for (int i = 0; i < logPotentials.length; i++) {
            logPotentials[i] -= logSum;
        }
    }

    /**
     * Convert a probability-space array to log-space in place.
     */
    static void toLogSpace(double[] probabilities) {
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] = probabilities[i] > 0.0 ? Math.log(probabilities[i]) : LOG_ZERO;
        }
    }

    /**
     * Convert a log-space array to probability-space in place.
     */
    static void toProbSpace(double[] logProbabilities) {
        for (int i = 0; i < logProbabilities.length; i++) {
            logProbabilities[i] = logProbabilities[i] == LOG_ZERO ? 0.0 : Math.exp(logProbabilities[i]);
        }
    }

    /**
     * Convert a probability-space array to log-space, returning a new array.
     */
    static double[] toLogSpaceCopy(double[] probabilities) {
        double[] result = new double[probabilities.length];
        for (int i = 0; i < probabilities.length; i++) {
            result[i] = probabilities[i] > 0.0 ? Math.log(probabilities[i]) : LOG_ZERO;
        }
        return result;
    }
}

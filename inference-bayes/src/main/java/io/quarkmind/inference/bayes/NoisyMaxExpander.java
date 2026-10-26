package io.quarkmind.inference.bayes;

import java.util.List;

public final class NoisyMaxExpander {

    private NoisyMaxExpander() {}

    public static double[][] expand(CptSpec.NoisyMax noisyMax, int numOutcomes) {
        List<NoisyMaxParent> parents = noisyMax.parents();
        double[] leak = noisyMax.leak();

        if (leak.length != numOutcomes) {
            throw new IllegalArgumentException(
                    "Leak distribution length " + leak.length + " does not match outcome count " + numOutcomes);
        }
        validateDistribution(leak, "leak");
        for (NoisyMaxParent p : parents) {
            if (p.distribution().length != numOutcomes) {
                throw new IllegalArgumentException(
                        "Parent '" + p.parentName() + "' distribution length " + p.distribution().length
                        + " does not match outcome count " + numOutcomes);
            }
            validateDistribution(p.distribution(), "parent '" + p.parentName() + "'");
        }

        int numParents = parents.size();
        int parentCombinations = numParents == 0 ? 1 : 1 << numParents;

        double[][] cpt = new double[parentCombinations][numOutcomes];

        double[] leakCumulative = cumulative(leak);

        double[][] parentCumulatives = new double[numParents][];
        for (int i = 0; i < numParents; i++) {
            parentCumulatives[i] = cumulative(parents.get(i).distribution());
        }

        for (int row = 0; row < parentCombinations; row++) {
            double prevCdf = 0.0;
            for (int k = 0; k < numOutcomes; k++) {
                double product = 1.0 - leakCumulative[k];
                for (int p = 0; p < numParents; p++) {
                    boolean parentActive = ((row >> (numParents - 1 - p)) & 1) == 0;
                    if (parentActive) {
                        product *= (1.0 - parentCumulatives[p][k]);
                    }
                }
                double cdf = 1.0 - product;
                cpt[row][k] = cdf - prevCdf;
                prevCdf = cdf;
            }
        }
        return cpt;
    }

    private static double[] cumulative(double[] distribution) {
        double[] cum = new double[distribution.length];
        cum[0] = distribution[0];
        for (int i = 1; i < distribution.length; i++) {
            cum[i] = cum[i - 1] + distribution[i];
        }
        return cum;
    }

    private static void validateDistribution(double[] distribution, String label) {
        double sum = 0;
        for (double v : distribution) {
            if (v < 0 || v > 1) {
                throw new IllegalArgumentException(
                        label + " distribution contains value out of [0,1]: " + v);
            }
            sum += v;
        }
        if (Math.abs(sum - 1.0) > 1e-6) {
            throw new IllegalArgumentException(
                    label + " distribution sums to " + sum + ", expected 1.0");
        }
    }

}

package io.quarkmind.inference.bayes;

import java.util.List;

public final class NoisyOrExpander {

    private NoisyOrExpander() {}

    public static double[][] expand(CptSpec.NoisyOr noisyOr, int numOutcomes) {
        if (numOutcomes != 2) {
            throw new IllegalArgumentException(
                "NoisyOr is only valid for binary child variables (2 outcomes), got " + numOutcomes);
        }
        List<ParentInfluence> parents = noisyOr.parentInfluences();
        double leak = noisyOr.leak();
        int numParents = parents.size();
        int parentCombinations = numParents == 0 ? 1 : 1 << numParents;

        double[][] cpt = new double[parentCombinations][2];
        for (int row = 0; row < parentCombinations; row++) {
            double pFalse = leak;
            for (int p = 0; p < numParents; p++) {
                boolean parentActive = ((row >> (numParents - 1 - p)) & 1) == 1;
                if (parentActive) {
                    pFalse *= (1.0 - parents.get(p).strength());
                }
            }
            cpt[row][0] = pFalse;
            cpt[row][1] = 1.0 - pFalse;
        }
        return cpt;
    }
}

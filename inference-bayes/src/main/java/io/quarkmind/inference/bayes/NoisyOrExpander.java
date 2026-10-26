package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.List;

public final class NoisyOrExpander {

    private NoisyOrExpander() {}

    public static double[][] expand(CptSpec.NoisyOr noisyOr, int numOutcomes) {
        if (numOutcomes > 2) {
            return NoisyMaxExpander.expand(convertToNoisyMax(noisyOr, numOutcomes), numOutcomes);
        }
        if (numOutcomes != 2) {
            throw new IllegalArgumentException(
                    "NoisyOr requires at least 2 outcomes, got " + numOutcomes);
        }
        List<ParentInfluence> parents            = noisyOr.parentInfluences();
        double                leak               = noisyOr.leak();
        int                   numParents         = parents.size();
        int                   parentCombinations = numParents == 0 ? 1 : 1 << numParents;

        double[][] cpt = new double[parentCombinations][2];
        for (int row = 0; row < parentCombinations; row++) {
            double pAbsent = 1.0 - leak;
            for (int p = 0; p < numParents; p++) {
                boolean parentActive = ((row >> (numParents - 1 - p)) & 1) == 0;
                if (parentActive) {
                    pAbsent *= (1.0 - parents.get(p).strength());
                }
            }
            cpt[row][0] = 1.0 - pAbsent;
            cpt[row][1] = pAbsent;
        }
        return cpt;
    }

    private static CptSpec.NoisyMax convertToNoisyMax(CptSpec.NoisyOr noisyOr, int numOutcomes) {
        List<NoisyMaxParent> maxParents = new ArrayList<>();
        for (ParentInfluence pi : noisyOr.parentInfluences()) {
            double[] dist = new double[numOutcomes];
            dist[0]               = pi.strength();
            dist[numOutcomes - 1] = 1.0 - pi.strength();
            maxParents.add(new NoisyMaxParent(pi.parentName(), dist));
        }
        double[] leak = new double[numOutcomes];
        leak[0]               = noisyOr.leak();
        leak[numOutcomes - 1] = 1.0 - noisyOr.leak();
        return new CptSpec.NoisyMax(maxParents, leak);
    }
}

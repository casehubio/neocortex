package io.quarkmind.inference.bayes;

import java.util.Arrays;

/**
 * Max-product variant of BayesProjection: replaces log-sum-exp with max.
 * Used by MAP inference to find the most likely joint assignment.
 */
public class MaxProjection {
    int[]    trgVarPos;
    int[]    trgVarPosMultipliers;
    double[] trgPotentials;
    int      trgPotentialIndex;

    BayesVariable[] srcVars;
    double[]        srcPotentials;
    int             srcPotentialIndex;

    int[]           path;

    public MaxProjection(BayesVariable[] srcVars, double[] srcPotentials, int[] trgVarPos, int[] trgVarPosMultipliers, double[] trgPotentials) {
        this.srcVars = srcVars;
        this.srcPotentials = srcPotentials;
        this.trgVarPos = trgVarPos;
        this.trgVarPosMultipliers = trgVarPosMultipliers;
        Arrays.fill(trgPotentials, LogSpace.LOG_ZERO);
        this.trgPotentials = trgPotentials;
        this.path = new int[srcVars.length];
    }

    public void project() {
        srcPotentialIndex = 0;
        trgPotentialIndex = 0;
        recurse(0, 0);
        BayesAbsorption.normalize(trgPotentials);
    }

    public void recurse(int currentVar, int targetVarPos) {
        int numberOfOutcomes = srcVars[currentVar].getOutcomes().length;

        boolean isTrgVar = false;
        int nextParentKeyPos = targetVarPos;
        if (this.trgVarPos.length > 0 && targetVarPos < this.trgVarPos.length && this.trgVarPos[targetVarPos] == currentVar) {
            nextParentKeyPos++;
            isTrgVar = true;
        }

        for (int j = 0; j < numberOfOutcomes; j++) {
            path[currentVar] = j;

            if (currentVar < srcVars.length - 1) {
                recurse(currentVar + 1, nextParentKeyPos);
            } else {
                trgPotentials[trgPotentialIndex] = Math.max(trgPotentials[trgPotentialIndex], srcPotentials[srcPotentialIndex++]);
            }
            if (isTrgVar) {
                trgPotentialIndex += trgVarPosMultipliers[targetVarPos];
            }
        }
        if (isTrgVar) {
            trgPotentialIndex -= (trgVarPosMultipliers[targetVarPos] * numberOfOutcomes);
        }
    }
}

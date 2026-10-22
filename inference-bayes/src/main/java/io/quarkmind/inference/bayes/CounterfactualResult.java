package io.quarkmind.inference.bayes;

public record CounterfactualResult(
        String interventionVar,
        String state,
        String targetVar,
        double[] observational,
        double[] interventional,
        String[] targetOutcomes
) {

    public double causalEffect(int outcomeIndex) {
        return interventional[outcomeIndex] - observational[outcomeIndex];
    }

    public boolean hasConfounding(double tolerance) {
        for (int i = 0; i < observational.length; i++) {
            if (Math.abs(observational[i] - interventional[i]) > tolerance) {
                return true;
            }
        }
        return false;
    }
}

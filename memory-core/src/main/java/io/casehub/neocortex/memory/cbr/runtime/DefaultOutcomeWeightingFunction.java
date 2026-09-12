package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.OutcomeWeightingFunction;

public class DefaultOutcomeWeightingFunction implements OutcomeWeightingFunction {

    private final double alpha;

    public DefaultOutcomeWeightingFunction(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public double apply(double similarity, double confidence) {
        return similarity * (1.0 - alpha + alpha * confidence);
    }
}

package io.casehub.neocortex.memory.cbr;

import java.util.OptionalDouble;

@FunctionalInterface
public interface TrustWeightingFunction {
    double apply(double similarity, double trustScore, OptionalDouble trustTrajectory);
}

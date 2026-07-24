package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.TrustWeightingFunction;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.OptionalDouble;

@DefaultBean
@ApplicationScoped
public class DefaultTrustWeightingFunction implements TrustWeightingFunction {

    private final double influence;
    private final double trajectorySensitivity;

    @Inject
    DefaultTrustWeightingFunction(TrustWeightingConfig config) {
        this(config.influence(), config.trajectorySensitivity());
    }

    DefaultTrustWeightingFunction(double influence, double trajectorySensitivity) {
        this.influence = influence;
        this.trajectorySensitivity = trajectorySensitivity;
    }

    @Override
    public double apply(double similarity, double trustScore, OptionalDouble trustTrajectory) {
        double weighted = similarity * (1.0 - influence + influence * trustScore);
        if (trustTrajectory.isPresent()) {
            double delta = trustTrajectory.getAsDouble();
            if (delta < 0) {
                weighted *= Math.max(0.5, 1.0 + trajectorySensitivity * delta);
            }
        }
        return weighted;
    }
}

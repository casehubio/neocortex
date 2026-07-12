package io.casehub.neocortex.memory.cbr;

public sealed interface WarpingConstraint {

    record Unconstrained() implements WarpingConstraint {}

    record SakoeChibaBand(int windowSize) implements WarpingConstraint {
        public SakoeChibaBand {
            if (windowSize < 1) {
                throw new IllegalArgumentException("windowSize must be >= 1, got: " + windowSize);
            }
        }
    }

    record ItakuraParallelogram(double maxSlope) implements WarpingConstraint {
        public ItakuraParallelogram {
            if (maxSlope <= 1.0) {
                throw new IllegalArgumentException("maxSlope must be > 1.0, got: " + maxSlope);
            }
        }
    }
}

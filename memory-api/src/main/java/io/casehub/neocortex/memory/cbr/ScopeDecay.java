package io.casehub.neocortex.memory.cbr;

public sealed interface ScopeDecay {

    double factor(int depthDistance);

    record Exponential(double base) implements ScopeDecay {
        public Exponential {
            if (base <= 0.0 || base > 1.0) {
                throw new IllegalArgumentException("base must be in (0, 1], got " + base);
            }
        }

        @Override
        public double factor(int depthDistance) {
            return Math.pow(base, depthDistance);
        }
    }

    record Linear(int maxDepth) implements ScopeDecay {
        public Linear {
            if (maxDepth < 1) {
                throw new IllegalArgumentException("maxDepth must be >= 1, got " + maxDepth);
            }
        }

        @Override
        public double factor(int depthDistance) {
            return Math.max(0.0, 1.0 - (double) depthDistance / maxDepth);
        }
    }

    record Step(double beyondExact) implements ScopeDecay {
        public Step {
            if (beyondExact < 0.0 || beyondExact > 1.0) {
                throw new IllegalArgumentException("beyondExact must be in [0, 1], got " + beyondExact);
            }
        }

        @Override
        public double factor(int depthDistance) {
            return depthDistance == 0 ? 1.0 : beyondExact;
        }
    }
}

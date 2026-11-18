package io.casehub.neocortex.cognition.situational;

public enum SignalTransform {
    IDENTITY {
        @Override public double apply(double x) { return x; }
    },
    POSITIVE_PART {
        @Override public double apply(double x) { return Math.max(0, x); }
    },
    NEGATIVE_PART {
        @Override public double apply(double x) { return Math.max(0, -x); }
    },
    COMPLEMENT {
        @Override public double apply(double x) { return 1.0 - x; }
    },
    ABOVE_FLOOR {
        @Override public double apply(double x) {
            throw new UnsupportedOperationException("ABOVE_FLOOR requires a floor parameter");
        }
        @Override public double applyWithFloor(double x, double floor) {
            if (x <= floor) return 0.0;
            return (x - floor) / (1.0 - floor);
        }
    },
    RISE_FROM_REST {
        @Override public double apply(double x) {
            throw new UnsupportedOperationException("RISE_FROM_REST requires rest/max parameters");
        }
        @Override public double applyFromRest(double activation, double rest, double max, double min) {
            if (max <= rest) return 0.0;
            return Math.max(0, (activation - rest) / (max - rest));
        }
    },
    DROP_FROM_REST {
        @Override public double apply(double x) {
            throw new UnsupportedOperationException("DROP_FROM_REST requires rest/min parameters");
        }
        @Override public double applyFromRest(double activation, double rest, double max, double min) {
            if (rest <= min) return 0.0;
            return Math.max(0, (rest - activation) / (rest - min));
        }
    };

    public abstract double apply(double x);

    public double applyWithFloor(double x, double floor) {
        return apply(x);
    }

    public double applyFromRest(double activation, double rest, double max, double min) {
        return apply(activation);
    }
}

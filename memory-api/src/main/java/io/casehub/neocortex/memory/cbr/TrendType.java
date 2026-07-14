package io.casehub.neocortex.memory.cbr;

public enum TrendType {
    SLOPE,
    DELTA,
    VOLATILITY,
    ACCELERATION,
    CHANGE_POINTS,
    DURATION,
    OBSERVATION_COUNT;

    public boolean isPerField() {
        return this == SLOPE || this == DELTA || this == VOLATILITY
               || this == ACCELERATION || this == CHANGE_POINTS;
    }
}

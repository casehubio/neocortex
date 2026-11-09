package io.casehub.neocortex.cognition.search;

final class FatigueState {
    private double accumulator;

    FatigueState(double initial) {
        this.accumulator = initial;
    }

    double accumulator() {
        return accumulator;
    }

    void setAccumulator(double value) {
        this.accumulator = value;
    }
}

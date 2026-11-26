package io.casehub.neocortex.caps;

public enum NodeRange {
    BIPOLAR,
    UNIPOLAR;

    /**
     * Activation value of an unstimulated node under this range's
     * activation function: tanh(0) = 0.0 for bipolar,
     * max(0, tanh(0)) = 0.0 for unipolar.
     */
    public double restingLevel() {
        return 0.0;
    }
}

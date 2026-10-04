package io.casehub.neocortex.caps;

public record ConnectionWeight(
    double excitatory,
    double inhibitory,
    double decayResistance,
    double precision
) {
    public double effectiveWeight() {
        return excitatory - inhibitory;
    }
}

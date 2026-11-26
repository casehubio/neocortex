package io.casehub.neocortex.caps;

public record ConnectionWeight(
        double excitatory,
        double precision
) {
    public double effectiveWeight() {
        return excitatory;
    }
}

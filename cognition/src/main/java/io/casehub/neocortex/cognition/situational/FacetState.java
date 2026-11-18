package io.casehub.neocortex.cognition.situational;

public record FacetState(
        double displacement,
        double homeOffset,
        double resilience,
        double exposure,
        double load,
        double promoted
) {
    public FacetState(double displacement, double homeOffset, double resilience, double exposure, double load) {
        this(displacement, homeOffset, resilience, exposure, load, 0.0);
    }

    public static FacetState initial() {
        return new FacetState(0, 0, 1.0, 0, 0, 0.0);
    }

    public FacetState withDisplacement(double d) {
        return new FacetState(d, homeOffset, resilience, exposure, load, promoted);
    }

    public FacetState withExposureAndLoad(double e, double l) {
        return new FacetState(displacement, homeOffset, resilience, e, l, promoted);
    }

    public FacetState withPromoted(double p) {
        return new FacetState(displacement, homeOffset, resilience, exposure, load, p);
    }

    public FacetState withHomeOffset(double ho) {
        return new FacetState(displacement, ho, resilience, exposure, load, promoted);
    }
}

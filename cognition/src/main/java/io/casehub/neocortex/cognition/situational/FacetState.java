package io.casehub.neocortex.cognition.situational;

public record FacetState(
        double displacement,
        double homeOffset,
        double resilience,
        double exposure,
        double load
) {
    public static FacetState initial() {
        return new FacetState(0, 0, 1.0, 0, 0);
    }

    public FacetState withDisplacement(double d) {
        return new FacetState(d, homeOffset, resilience, exposure, load);
    }

    public FacetState withHomeOffset(double o) {
        return new FacetState(displacement, o, resilience, exposure, load);
    }

    public FacetState withResilience(double r) {
        return new FacetState(displacement, homeOffset, r, exposure, load);
    }

    public FacetState withExposure(double e) {
        return new FacetState(displacement, homeOffset, resilience, e, load);
    }

    public FacetState withLoad(double l) {
        return new FacetState(displacement, homeOffset, resilience, exposure, l);
    }

    public FacetState withExposureAndLoad(double e, double l) {
        return new FacetState(displacement, homeOffset, resilience, e, l);
    }
}

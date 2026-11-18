package io.casehub.neocortex.cognition.situational;

import java.time.Instant;

public record FacetState(
        double displacement,
        double homeOffset,
        double resilience,
        double exposure,
        double load,
        double promoted,
        Instant saturatedSince,
        Instant cappedSince
) {
    public FacetState(double displacement, double homeOffset, double resilience, double exposure, double load, double promoted) {
        this(displacement, homeOffset, resilience, exposure, load, promoted, null, null);
    }

    public FacetState(double displacement, double homeOffset, double resilience, double exposure, double load) {
        this(displacement, homeOffset, resilience, exposure, load, 0.0, null, null);
    }

    public static FacetState initial() {
        return new FacetState(0, 0, 1.0, 0, 0, 0.0, null, null);
    }

    public FacetState withDisplacement(double d) {
        return new FacetState(d, homeOffset, resilience, exposure, load, promoted, saturatedSince, cappedSince);
    }

    public FacetState withExposureAndLoad(double e, double l) {
        return new FacetState(displacement, homeOffset, resilience, e, l, promoted, saturatedSince, cappedSince);
    }

    public FacetState withPromoted(double p) {
        return new FacetState(displacement, homeOffset, resilience, exposure, load, p, saturatedSince, cappedSince);
    }

    public FacetState withHomeOffset(double ho) {
        return new FacetState(displacement, ho, resilience, exposure, load, promoted, saturatedSince, cappedSince);
    }

    public FacetState withSaturatedSince(Instant ss) {
        return new FacetState(displacement, homeOffset, resilience, exposure, load, promoted, ss, cappedSince);
    }

    public FacetState withCappedSince(Instant cs) {
        return new FacetState(displacement, homeOffset, resilience, exposure, load, promoted, saturatedSince, cs);
    }
}

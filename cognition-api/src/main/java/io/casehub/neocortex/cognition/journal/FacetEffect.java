package io.casehub.neocortex.cognition.journal;

public record FacetEffect(String facet, double facetScore, String field, double before, double after) {

    public double delta() { return after - before; }
}

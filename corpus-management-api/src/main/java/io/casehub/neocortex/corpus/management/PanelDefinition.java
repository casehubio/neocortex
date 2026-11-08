package io.casehub.neocortex.corpus.management;

public record PanelDefinition(String id, double x, double y, double w, double h) {
    public PanelDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (x < 0.0 || x > 1.0) throw new IllegalArgumentException("x must be in [0.0, 1.0]");
        if (y < 0.0 || y > 1.0) throw new IllegalArgumentException("y must be in [0.0, 1.0]");
        if (w <= 0.0 || w > 1.0) throw new IllegalArgumentException("w must be in (0.0, 1.0]");
        if (h <= 0.0 || h > 1.0) throw new IllegalArgumentException("h must be in (0.0, 1.0]");
    }
}

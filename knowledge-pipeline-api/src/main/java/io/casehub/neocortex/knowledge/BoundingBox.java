package io.casehub.neocortex.knowledge;

public record BoundingBox(double minLat, double minLng,
                           double maxLat, double maxLng) {
    public BoundingBox {
        if (minLat > maxLat) throw new IllegalArgumentException("minLat > maxLat");
        if (minLng > maxLng) throw new IllegalArgumentException("minLng > maxLng");
    }
}

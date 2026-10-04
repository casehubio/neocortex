package io.casehub.neocortex.knowledge.resolution;

import io.casehub.connectors.location.model.Coordinates;

public final class Haversine {

    private static final double R = 6_371_000;

    private Haversine() {}

    public static double distanceMeters(Coordinates a, Coordinates b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double aLat = Math.toRadians(a.lat());
        double bLat = Math.toRadians(b.lat());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(aLat) * Math.cos(bLat)
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}

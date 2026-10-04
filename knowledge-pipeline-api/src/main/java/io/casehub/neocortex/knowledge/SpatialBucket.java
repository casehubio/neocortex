package io.casehub.neocortex.knowledge;

public final class SpatialBucket {

    private SpatialBucket() {}

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    public static String encode(double lat, double lng, int precision) {
        double latMin = -90, latMax = 90;
        double lngMin = -180, lngMax = 180;
        boolean isLng = true;
        int bit = 0;
        int ch = 0;
        StringBuilder hash = new StringBuilder(precision);
        while (hash.length() < precision) {
            double mid;
            if (isLng) {
                mid = (lngMin + lngMax) / 2;
                if (lng >= mid) { ch |= (1 << (4 - bit)); lngMin = mid; }
                else { lngMax = mid; }
            } else {
                mid = (latMin + latMax) / 2;
                if (lat >= mid) { ch |= (1 << (4 - bit)); latMin = mid; }
                else { latMax = mid; }
            }
            isLng = !isLng;
            if (bit < 4) { bit++; }
            else { hash.append(BASE32.charAt(ch)); bit = 0; ch = 0; }
        }
        return hash.toString();
    }
}

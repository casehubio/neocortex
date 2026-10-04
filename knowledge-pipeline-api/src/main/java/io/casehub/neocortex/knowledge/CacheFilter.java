package io.casehub.neocortex.knowledge;

public record CacheFilter(String category, String priceLevel,
                           Double minRating, Integer maxRadius) {

    public static CacheFilter none() {
        return new CacheFilter(null, null, null, null);
    }
}

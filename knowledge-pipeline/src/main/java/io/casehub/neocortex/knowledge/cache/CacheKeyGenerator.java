package io.casehub.neocortex.knowledge.cache;

import io.casehub.neocortex.knowledge.KnowledgeQuery;
import io.casehub.neocortex.knowledge.NormalizedQuery;
import io.casehub.neocortex.knowledge.SpatialBucket;

import java.text.Normalizer;

public final class CacheKeyGenerator {

    private CacheKeyGenerator() {}

    public static NormalizedQuery generate(KnowledgeQuery query, int geohashPrecision) {
        String key = switch (query) {
            case KnowledgeQuery.TextSearch t ->
                "TEXT:" + normalizeText(t.query());
            case KnowledgeQuery.NearbySearch n ->
                "NEARBY:" + SpatialBucket.encode(
                    n.center().lat(), n.center().lng(), geohashPrecision)
                    + ":" + n.radiusMeters();
            case KnowledgeQuery.CategorySearch c ->
                "CATEGORY:" + c.category().toLowerCase().strip()
                    + ":" + SpatialBucket.encode(
                        c.center().lat(), c.center().lng(), geohashPrecision)
                    + ":" + c.radiusMeters();
        };
        return new NormalizedQuery(query, key);
    }

    static String normalizeText(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .toLowerCase().strip().replaceAll("\\s+", " ");
    }
}

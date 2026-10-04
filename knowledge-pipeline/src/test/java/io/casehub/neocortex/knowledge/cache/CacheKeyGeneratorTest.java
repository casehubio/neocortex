package io.casehub.neocortex.knowledge.cache;

import io.casehub.connectors.location.model.Coordinates;
import io.casehub.neocortex.knowledge.CacheFilter;
import io.casehub.neocortex.knowledge.KnowledgeQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyGeneratorTest {

    @Test
    void textSearchNormalizesCase() {
        var q = new KnowledgeQuery.TextSearch("Find Italian  Restaurants");
        var nq = CacheKeyGenerator.generate(q, 6);
        assertThat(nq.cacheKey()).isEqualTo("TEXT:find italian restaurants");
    }

    @Test
    void textSearchTrimsWhitespace() {
        var q = new KnowledgeQuery.TextSearch("  pizza  near me  ");
        var nq = CacheKeyGenerator.generate(q, 6);
        assertThat(nq.cacheKey()).isEqualTo("TEXT:pizza near me");
    }

    @Test
    void nearbySearchRoundsToGeohash() {
        var q1 = new KnowledgeQuery.NearbySearch(
            new Coordinates(51.5317, -0.1240), 1000, CacheFilter.none());
        var q2 = new KnowledgeQuery.NearbySearch(
            new Coordinates(51.5320, -0.1235), 1000, CacheFilter.none());
        assertThat(CacheKeyGenerator.generate(q1, 6).cacheKey())
            .isEqualTo(CacheKeyGenerator.generate(q2, 6).cacheKey());
    }

    @Test
    void nearbySearchIncludesRadius() {
        var q = new KnowledgeQuery.NearbySearch(
            new Coordinates(51.5, -0.1), 1000, CacheFilter.none());
        assertThat(CacheKeyGenerator.generate(q, 6).cacheKey())
            .endsWith(":1000");
    }

    @Test
    void categorySearchIncludesCategoryInKey() {
        var q = new KnowledgeQuery.CategorySearch(
            "italian", new Coordinates(51.5, -0.1), 1000);
        var nq = CacheKeyGenerator.generate(q, 6);
        assertThat(nq.cacheKey()).startsWith("CATEGORY:italian:");
    }

    @Test
    void categorySearchNormalizesCategory() {
        var q = new KnowledgeQuery.CategorySearch(
            " ITALIAN ", new Coordinates(51.5, -0.1), 1000);
        var nq = CacheKeyGenerator.generate(q, 6);
        assertThat(nq.cacheKey()).startsWith("CATEGORY:italian:");
    }

    @Test
    void differentRadiiProduceDifferentKeys() {
        var q1 = new KnowledgeQuery.NearbySearch(
            new Coordinates(51.5, -0.1), 1000, CacheFilter.none());
        var q2 = new KnowledgeQuery.NearbySearch(
            new Coordinates(51.5, -0.1), 5000, CacheFilter.none());
        assertThat(CacheKeyGenerator.generate(q1, 6).cacheKey())
            .isNotEqualTo(CacheKeyGenerator.generate(q2, 6).cacheKey());
    }

    @Test
    void normalizedQueryPreservesOriginalQuery() {
        var q = new KnowledgeQuery.TextSearch("pizza");
        var nq = CacheKeyGenerator.generate(q, 6);
        assertThat(nq.query()).isEqualTo(q);
    }
}

package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveFilterTest {

    private static RetrievedChunk chunk(String id, double score) {
        return new RetrievedChunk("content", id, score, Map.of());
    }

    private static final AdaptiveSearchConfig CONFIG = new AdaptiveSearchConfig(0.3, 0.2, 2, 2.0);
    private static final AdaptiveFilterOptions<RetrievedChunk> OPTIONS =
        AdaptiveFilterOptions.of(CONFIG, RetrievedChunk::relevanceScore);

    @Test
    void emptyInput_returnsEmpty() {
        var result = AdaptiveFilter.filter(List.of(), 10, OPTIONS);
        assertThat(result).isEmpty();
    }

    @Test
    void floor_removesLowScores() {
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.5), chunk("c", 0.1));
        var result = AdaptiveFilter.filter(chunks, 10, OPTIONS);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b");
    }

    @Test
    void gapTrim_removesAfterLargeGap() {
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.85), chunk("c", 0.5), chunk("d", 0.45));
        var result = AdaptiveFilter.filter(chunks, 10, OPTIONS);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b");
    }

    @Test
    void minResults_guaranteesMinimum() {
        var config = new AdaptiveSearchConfig(0.8, 0.2, 3, 2.0);
        var opts = AdaptiveFilterOptions.of(config, RetrievedChunk::relevanceScore);
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.5), chunk("c", 0.4));
        var result = AdaptiveFilter.filter(chunks, 10, opts);
        assertThat(result).hasSize(3);
    }

    @Test
    void minResults_returnsAllWhenFewerThanMinExist() {
        var config = new AdaptiveSearchConfig(0.8, 0.2, 5, 2.0);
        var opts = AdaptiveFilterOptions.of(config, RetrievedChunk::relevanceScore);
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.5));
        var result = AdaptiveFilter.filter(chunks, 10, opts);
        assertThat(result).hasSize(2);
    }

    @Test
    void requestedLimit_capsResults() {
        var config = new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0);
        var opts = AdaptiveFilterOptions.of(config, RetrievedChunk::relevanceScore);
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.8), chunk("c", 0.7));
        var result = AdaptiveFilter.filter(chunks, 2, opts);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b");
    }

    @Test
    void sortsResultsByScoreDescending() {
        var chunks = List.of(chunk("c", 0.5), chunk("a", 0.9), chunk("b", 0.7));
        var config = new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0);
        var opts = AdaptiveFilterOptions.of(config, RetrievedChunk::relevanceScore);
        var result = AdaptiveFilter.filter(chunks, 10, opts);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b", "c");
    }

    @Test
    void floorAndGapInteract_gapTrimmedThenFloorApplied() {
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.85), chunk("c", 0.4), chunk("d", 0.35));
        var result = AdaptiveFilter.filter(chunks, 10, OPTIONS);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b");
    }

    // --- CE boundary tests ---

    @Test
    void ceBoundary_truncatesAtCeToNonCeBoundary() {
        record Item(String id, double score, boolean hasCe) {}
        var items = List.of(
            new Item("a", 0.9, true), new Item("b", 0.8, true),
            new Item("c", 0.7, false), new Item("d", 0.6, false));
        var config = new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0);
        var opts = AdaptiveFilterOptions.of(config, Item::score)
            .withCeBoundary(Item::hasCe);
        var result = AdaptiveFilter.filter(items, 10, opts);
        assertThat(result).extracting(Item::id).containsExactly("a", "b");
    }

    @Test
    void ceBoundary_allCeItemsPassThrough() {
        record Item(String id, double score, boolean hasCe) {}
        var items = List.of(
            new Item("a", 0.9, true), new Item("b", 0.8, true));
        var config = new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0);
        var opts = AdaptiveFilterOptions.of(config, Item::score)
            .withCeBoundary(Item::hasCe);
        var result = AdaptiveFilter.filter(items, 10, opts);
        assertThat(result).extracting(Item::id).containsExactly("a", "b");
    }

    @Test
    void ceBoundary_noCeItemsPassThrough() {
        record Item(String id, double score, boolean hasCe) {}
        var items = List.of(
            new Item("a", 0.9, false), new Item("b", 0.8, false));
        var config = new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0);
        var opts = AdaptiveFilterOptions.of(config, Item::score)
            .withCeBoundary(Item::hasCe);
        var result = AdaptiveFilter.filter(items, 10, opts);
        assertThat(result).extracting(Item::id).containsExactly("a", "b");
    }

    // --- Cluster extension tests ---

    @Test
    void clusterExtension_extendsIntoDenseCluster() {
        var chunks = List.of(
            chunk("a", 0.9), chunk("b", 0.88), chunk("c", 0.86),
            chunk("d", 0.84), chunk("e", 0.5));
        var config = new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0);
        var opts = AdaptiveFilterOptions.of(config, RetrievedChunk::relevanceScore)
            .withClusterExtension(0.05);
        var result = AdaptiveFilter.filter(chunks, 2, opts);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b", "c", "d");
    }

    @Test
    void clusterExtension_stopsAtGap() {
        var chunks = List.of(
            chunk("a", 0.9), chunk("b", 0.88), chunk("c", 0.7));
        var config = new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0);
        var opts = AdaptiveFilterOptions.of(config, RetrievedChunk::relevanceScore)
            .withClusterExtension(0.05);
        var result = AdaptiveFilter.filter(chunks, 2, opts);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b");
    }

    // --- Generic type test ---

    @Test
    void genericType_worksWithNonChunkType() {
        record SearchResult(String id, double score) {}
        var items = List.of(
            new SearchResult("x", 0.9), new SearchResult("y", 0.5),
            new SearchResult("z", 0.1));
        var config = new AdaptiveSearchConfig(0.3, 0.2, 2, 2.0);
        var opts = AdaptiveFilterOptions.of(config, SearchResult::score);
        var result = AdaptiveFilter.filter(items, 10, opts);
        assertThat(result).extracting(SearchResult::id)
            .containsExactly("x", "y");
    }
}

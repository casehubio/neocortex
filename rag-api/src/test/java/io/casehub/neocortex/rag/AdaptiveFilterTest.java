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

    @Test
    void emptyInput_returnsEmpty() {
        var result = AdaptiveFilter.filter(List.of(), 10, CONFIG);
        assertThat(result).isEmpty();
    }

    @Test
    void floor_removesLowScores() {
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.5), chunk("c", 0.1));
        var result = AdaptiveFilter.filter(chunks, 10, CONFIG);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b");
    }

    @Test
    void gapTrim_removesAfterLargeGap() {
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.85), chunk("c", 0.5), chunk("d", 0.45));
        var result = AdaptiveFilter.filter(chunks, 10, CONFIG);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b");
    }

    @Test
    void minResults_guaranteesMinimum() {
        var config = new AdaptiveSearchConfig(0.8, 0.2, 3, 2.0);
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.5), chunk("c", 0.4));
        var result = AdaptiveFilter.filter(chunks, 10, config);
        assertThat(result).hasSize(3);
    }

    @Test
    void minResults_returnsAllWhenFewerThanMinExist() {
        var config = new AdaptiveSearchConfig(0.8, 0.2, 5, 2.0);
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.5));
        var result = AdaptiveFilter.filter(chunks, 10, config);
        assertThat(result).hasSize(2);
    }

    @Test
    void requestedLimit_capsResults() {
        var config = new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0);
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.8), chunk("c", 0.7));
        var result = AdaptiveFilter.filter(chunks, 2, config);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b");
    }

    @Test
    void sortsResultsByScoreDescending() {
        var chunks = List.of(chunk("c", 0.5), chunk("a", 0.9), chunk("b", 0.7));
        var config = new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0);
        var result = AdaptiveFilter.filter(chunks, 10, config);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b", "c");
    }

    @Test
    void floorAndGapInteract_gapTrimmedThenFloorApplied() {
        var chunks = List.of(chunk("a", 0.9), chunk("b", 0.85), chunk("c", 0.4), chunk("d", 0.35));
        var result = AdaptiveFilter.filter(chunks, 10, CONFIG);
        assertThat(result).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b");
    }
}

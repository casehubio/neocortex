package io.casehub.neocortex.rag.scoring;

import io.casehub.neocortex.rag.AdaptiveSearchConfig;
import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PayloadFilter;
import io.casehub.neocortex.rag.PostRetrievalScorer;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.ScoringContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveSearchWrapperTest {

    private static final CorpusRef CORPUS = new CorpusRef("test", "corpus");
    private static final AdaptiveSearchConfig CONFIG = new AdaptiveSearchConfig(0.3, 0.2, 2, 2.0);

    private static RetrievedChunk chunk(String id, double score) {
        return new RetrievedChunk("content", id, score, Map.of());
    }

    private static CaseRetriever fixedRetriever(List<RetrievedChunk> chunks) {
        return (query, corpus, maxResults, filter) -> chunks;
    }

    @Test
    void overfetchMultiplier_applied() {
        var capturedLimit = new int[1];
        CaseRetriever retriever = (query, corpus, maxResults, filter) -> {
            capturedLimit[0] = maxResults;
            return List.of(chunk("a", 0.9));
        };
        var wrapper = new AdaptiveSearchWrapper(retriever, List.of(), CONFIG);
        wrapper.search(RetrievalQuery.of("test"), CORPUS, 5, ScoringContext.EMPTY);
        assertThat(capturedLimit[0]).isEqualTo(10);
    }

    @Test
    void scorersAdjustChunkScores() {
        PostRetrievalScorer halver = (chunk, query, ctx) -> 0.5;
        var wrapper = new AdaptiveSearchWrapper(
            fixedRetriever(List.of(chunk("a", 1.0), chunk("b", 0.8))),
            List.of(halver),
            new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0));

        var results = wrapper.search(RetrievalQuery.of("test"), CORPUS, 10, ScoringContext.EMPTY);
        assertThat(results).extracting(RetrievedChunk::relevanceScore)
            .allMatch(s -> s <= 0.5);
    }

    @Test
    void multipleScorersCompose() {
        PostRetrievalScorer halver = (chunk, query, ctx) -> 0.5;
        var wrapper = new AdaptiveSearchWrapper(
            fixedRetriever(List.of(chunk("a", 1.0))),
            List.of(halver, halver),
            new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0));

        var results = wrapper.search(RetrievalQuery.of("test"), CORPUS, 10, ScoringContext.EMPTY);
        assertThat(results.getFirst().relevanceScore()).isEqualTo(0.25);
    }

    @Test
    void adaptiveFilterApplied() {
        var wrapper = new AdaptiveSearchWrapper(
            fixedRetriever(List.of(chunk("a", 0.9), chunk("b", 0.5), chunk("c", 0.1))),
            List.of(),
            CONFIG);

        var results = wrapper.search(RetrievalQuery.of("test"), CORPUS, 10, ScoringContext.EMPTY);
        assertThat(results).extracting(RetrievedChunk::sourceDocumentId)
            .containsExactly("a", "b");
    }

    @Test
    void emptyRetrieverReturnsEmpty() {
        var wrapper = new AdaptiveSearchWrapper(
            fixedRetriever(List.of()),
            List.of(),
            CONFIG);

        var results = wrapper.search(RetrievalQuery.of("test"), CORPUS, 10, ScoringContext.EMPTY);
        assertThat(results).isEmpty();
    }

    @Test
    void noScorers_passesThrough() {
        var wrapper = new AdaptiveSearchWrapper(
            fixedRetriever(List.of(chunk("a", 0.9), chunk("b", 0.8))),
            List.of(),
            new AdaptiveSearchConfig(0.0, 1.0, 0, 1.0));

        var results = wrapper.search(RetrievalQuery.of("test"), CORPUS, 10, ScoringContext.EMPTY);
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().relevanceScore()).isEqualTo(0.9);
    }
}

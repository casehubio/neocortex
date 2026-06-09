package io.casehub.rag.runtime;

import io.casehub.rag.CaseRetriever;
import io.casehub.rag.CorpusRef;
import io.casehub.rag.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingToReactiveCaseRetrieverTest {

    private BlockingToReactiveCaseRetriever bridge;

    @BeforeEach
    void setUp() {
        CaseRetriever blocking = (query, corpus, maxResults) ->
            List.of(new RetrievedChunk("result for " + query, "d1", 0.95, Map.of()));
        bridge = new BlockingToReactiveCaseRetriever(blocking);
    }

    @Test
    void retrieveDelegatesToBlocking() {
        var corpus = new CorpusRef("t1", "docs");
        List<RetrievedChunk> result = bridge.retrieve("test query", corpus, 5)
            .await().indefinitely();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("result for test query");
        assertThat(result.get(0).relevanceScore()).isEqualTo(0.95);
    }

    @Test
    void retrieveEmptyFromBlockingReturnsEmpty() {
        CaseRetriever empty = (query, corpus, maxResults) -> List.of();
        bridge = new BlockingToReactiveCaseRetriever(empty);
        var corpus = new CorpusRef("t1", "docs");
        List<RetrievedChunk> result = bridge.retrieve("q", corpus, 10)
            .await().indefinitely();
        assertThat(result).isEmpty();
    }
}

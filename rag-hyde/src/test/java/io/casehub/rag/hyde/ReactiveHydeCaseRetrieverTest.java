package io.casehub.rag.hyde;

import io.casehub.rag.CorpusRef;
import io.casehub.rag.QueryExpander;
import io.casehub.rag.ReactiveCaseRetriever;
import io.casehub.rag.RetrievalQuery;
import io.casehub.rag.RetrievedChunk;
import io.casehub.rag.testing.InMemoryQueryExpander;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveHydeCaseRetrieverTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant-1", "test-corpus");

    @Test
    void delegatesWithExpandedQuery() {
        var capturedQuery = new AtomicReference<RetrievalQuery>();
        ReactiveCaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQuery.set(query);
            return Uni.createFrom().item(List.of(chunk("result", "doc1", 0.9)));
        };

        var hyde = new ReactiveHydeCaseRetriever(delegate, new InMemoryQueryExpander());
        var results = hyde.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null)
            .await().indefinitely();

        assertThat(results).hasSize(1);
        assertThat(capturedQuery.get().text()).isEqualTo("original");
        assertThat(capturedQuery.get().expandedText()).isEqualTo("hypothetical: original");
    }

    @Test
    void failSafeOnExpanderError() {
        var capturedQuery = new AtomicReference<RetrievalQuery>();
        ReactiveCaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQuery.set(query);
            return Uni.createFrom().item(List.of(chunk("result", "doc1", 0.9)));
        };
        QueryExpander failingExpander = query -> {
            throw new RuntimeException("LLM timeout");
        };

        var hyde = new ReactiveHydeCaseRetriever(delegate, failingExpander);
        var results = hyde.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null)
            .await().indefinitely();

        assertThat(results).hasSize(1);
        assertThat(capturedQuery.get().expandedText()).isNull();
    }

    private static RetrievedChunk chunk(String content, String docId, double score) {
        return new RetrievedChunk(content, docId, score, Map.of());
    }
}

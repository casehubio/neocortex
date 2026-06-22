package io.casehub.rag.hyde;

import io.casehub.rag.CaseRetriever;
import io.casehub.rag.CorpusRef;
import io.casehub.rag.QueryExpander;
import io.casehub.rag.RetrievalQuery;
import io.casehub.rag.RetrievedChunk;
import io.casehub.rag.testing.InMemoryQueryExpander;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HydeCaseRetrieverTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant-1", "test-corpus");

    @Test
    void delegatesWithExpandedQuery() {
        var capturedQuery = new AtomicReference<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQuery.set(query);
            return List.of(chunk("result", "doc1", 0.9));
        };
        var expander = new InMemoryQueryExpander();

        var hyde = new HydeCaseRetriever(delegate, expander);
        var results = hyde.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);

        assertThat(results).hasSize(1);
        assertThat(capturedQuery.get().text()).isEqualTo("original");
        assertThat(capturedQuery.get().expandedText()).isEqualTo("hypothetical: original");
        assertThat(capturedQuery.get().searchText()).isEqualTo("hypothetical: original");
    }

    @Test
    void failSafeOnExpanderError() {
        var capturedQuery = new AtomicReference<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQuery.set(query);
            return List.of(chunk("result", "doc1", 0.9));
        };
        QueryExpander failingExpander = query -> {
            throw new RuntimeException("LLM timeout");
        };

        var hyde = new HydeCaseRetriever(delegate, failingExpander);
        var results = hyde.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);

        assertThat(results).hasSize(1);
        assertThat(capturedQuery.get().text()).isEqualTo("original");
        assertThat(capturedQuery.get().expandedText()).isNull();
    }

    @Test
    void passesCorpusAndFilterThrough() {
        var capturedCorpus = new AtomicReference<CorpusRef>();
        var capturedMax = new int[1];
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedCorpus.set(corpus);
            capturedMax[0] = maxResults;
            return List.of();
        };

        var hyde = new HydeCaseRetriever(delegate, new InMemoryQueryExpander());
        hyde.retrieve(RetrievalQuery.of("q"), CORPUS, 7, null);

        assertThat(capturedCorpus.get()).isEqualTo(CORPUS);
        assertThat(capturedMax[0]).isEqualTo(7);
    }

    private static RetrievedChunk chunk(String content, String docId, double score) {
        return new RetrievedChunk(content, docId, score, Map.of());
    }
}

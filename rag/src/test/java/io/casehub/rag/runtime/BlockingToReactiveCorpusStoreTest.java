package io.casehub.rag.runtime;

import io.casehub.rag.ChunkInput;
import io.casehub.rag.CorpusRef;
import io.casehub.rag.CorpusStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingToReactiveCorpusStoreTest {

    private RecordingCorpusStore blocking;
    private BlockingToReactiveCorpusStore bridge;

    @BeforeEach
    void setUp() {
        blocking = new RecordingCorpusStore();
        bridge = new BlockingToReactiveCorpusStore(blocking);
    }

    @Test
    void ingestDelegatesToBlocking() {
        var corpus = new CorpusRef("t1", "docs");
        var chunks = List.of(new ChunkInput("hello", "d1", Map.of()));
        bridge.ingest(corpus, chunks).await().indefinitely();
        assertThat(blocking.calls).containsExactly("ingest:t1:docs");
    }

    @Test
    void deleteDocumentDelegatesToBlocking() {
        var corpus = new CorpusRef("t1", "docs");
        bridge.deleteDocument(corpus, "d1").await().indefinitely();
        assertThat(blocking.calls).containsExactly("deleteDocument:t1:docs:d1");
    }

    @Test
    void deleteCorpusDelegatesToBlocking() {
        var corpus = new CorpusRef("t1", "docs");
        bridge.deleteCorpus(corpus).await().indefinitely();
        assertThat(blocking.calls).containsExactly("deleteCorpus:t1:docs");
    }

    @Test
    void listDocumentsDelegatesToBlocking() {
        var corpus = new CorpusRef("t1", "docs");
        blocking.documentsToReturn = List.of("d1", "d2");
        List<String> result = bridge.listDocuments(corpus).await().indefinitely();
        assertThat(result).containsExactly("d1", "d2");
        assertThat(blocking.calls).containsExactly("listDocuments:t1:docs");
    }

    static class RecordingCorpusStore implements CorpusStore {
        final List<String> calls = new ArrayList<>();
        List<String> documentsToReturn = List.of();

        @Override
        public void ingest(CorpusRef corpus, List<ChunkInput> chunks) {
            calls.add("ingest:" + corpus.tenantId() + ":" + corpus.corpusName());
        }

        @Override
        public void deleteDocument(CorpusRef corpus, String sourceDocumentId) {
            calls.add("deleteDocument:" + corpus.tenantId() + ":" + corpus.corpusName()
                + ":" + sourceDocumentId);
        }

        @Override
        public void deleteCorpus(CorpusRef corpus) {
            calls.add("deleteCorpus:" + corpus.tenantId() + ":" + corpus.corpusName());
        }

        @Override
        public List<String> listDocuments(CorpusRef corpus) {
            calls.add("listDocuments:" + corpus.tenantId() + ":" + corpus.corpusName());
            return documentsToReturn;
        }
    }
}

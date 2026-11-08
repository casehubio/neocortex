package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.rag.ChunkInput;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.rag.runtime.IngestionContext;
import io.casehub.neocortex.rag.runtime.IngestionPipeline;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteCorpusEntryStoreTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant-1", "test-corpus");

    private SqliteCorpusEntryStore store;
    private StubIngestor ingestor;

    @BeforeEach
    void setUp() {
        var ds = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        ingestor = new StubIngestor();
        var pipeline = new IngestionPipeline(ingestor);
        var context = new IngestionContext(CORPUS, null, null);
        store = new SqliteCorpusEntryStore(ds, pipeline, context, null, null);
    }

    @Test
    void addAndGet() {
        var entry = new CorpusEntry("e1", Map.of("title", "Test", "domain", "jvm"), "Body text", null);
        store.add(CORPUS, entry);

        var retrieved = store.get(CORPUS, "e1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().id()).isEqualTo("e1");
        assertThat(retrieved.get().stringField("title")).isEqualTo("Test");
        assertThat(retrieved.get().body()).isEqualTo("Body text");
    }

    @Test
    void addIngestsEmbedding() {
        var entry = new CorpusEntry("e1", Map.of("title", "Test"), "Body text for embedding", null);
        store.add(CORPUS, entry);

        assertThat(ingestor.ingested).hasSize(1);
        assertThat(ingestor.ingested.get(0).content()).isEqualTo("Body text for embedding");
        assertThat(ingestor.ingested.get(0).sourceDocumentId()).isEqualTo("e1");
    }

    @Test
    void getReturnsEmptyForMissing() {
        assertThat(store.get(CORPUS, "nonexistent")).isEmpty();
    }

    @Test
    void updateModifiesEntry() {
        var entry = new CorpusEntry("e1", Map.of("title", "Original"), "Original body", null);
        store.add(CORPUS, entry);

        var updated = new CorpusEntry("e1", Map.of("title", "Updated"), "Updated body", null);
        store.update(CORPUS, "e1", updated);

        var retrieved = store.get(CORPUS, "e1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().stringField("title")).isEqualTo("Updated");
        assertThat(retrieved.get().body()).isEqualTo("Updated body");
    }

    @Test
    void updateReIngestsEmbedding() {
        var entry = new CorpusEntry("e1", Map.of("title", "Test"), "Body v1", null);
        store.add(CORPUS, entry);
        ingestor.ingested.clear();
        ingestor.deleted.clear();

        var updated = new CorpusEntry("e1", Map.of("title", "Test"), "Body v2", null);
        store.update(CORPUS, "e1", updated);

        assertThat(ingestor.deleted).contains("e1");
        assertThat(ingestor.ingested).hasSize(1);
        assertThat(ingestor.ingested.get(0).content()).isEqualTo("Body v2");
    }

    @Test
    void removeDeletesEntry() {
        var entry = new CorpusEntry("e1", Map.of("title", "Test"), "Body", null);
        store.add(CORPUS, entry);

        store.remove(CORPUS, "e1");

        assertThat(store.get(CORPUS, "e1")).isEmpty();
        assertThat(ingestor.deleted).contains("e1");
    }

    @Test
    void addPreservesArtifactRef() {
        var entry = new CorpusEntry("e1", Map.of("title", "Test"), "Body",
            "corpus://test-corpus/artifacts/doc.zip#42");
        store.add(CORPUS, entry);

        var retrieved = store.get(CORPUS, "e1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().artifactRef()).isEqualTo("corpus://test-corpus/artifacts/doc.zip#42");
    }

    @Test
    void entriesIsolatedByCorpus() {
        var corpus2 = new CorpusRef("tenant-1", "other-corpus");
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "A"), "Body A", null));
        store.add(corpus2, new CorpusEntry("e1", Map.of("title", "B"), "Body B", null));

        assertThat(store.get(CORPUS, "e1").get().stringField("title")).isEqualTo("A");
        assertThat(store.get(corpus2, "e1").get().stringField("title")).isEqualTo("B");
    }

    // --- Stubs ---

    static class StubIngestor implements EmbeddingIngestor {
        final List<ChunkInput> ingested = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();

        @Override
        public void ingest(CorpusRef corpus, List<ChunkInput> chunks) {
            ingested.addAll(chunks);
        }

        @Override
        public void deleteDocument(CorpusRef corpus, String sourceDocumentId) {
            deleted.add(sourceDocumentId);
        }

        @Override
        public void deleteCorpus(CorpusRef corpus) {}

        @Override
        public List<String> listDocuments(CorpusRef corpus) {
            return List.of();
        }
    }
}

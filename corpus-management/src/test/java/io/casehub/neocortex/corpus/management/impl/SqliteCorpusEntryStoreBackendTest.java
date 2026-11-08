package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.CorpusStore;
import io.casehub.neocortex.corpus.management.ContentSerializer;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.zip.FlatCorpusStore;
import io.casehub.neocortex.rag.ChunkInput;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.rag.runtime.IngestionContext;
import io.casehub.neocortex.rag.runtime.IngestionPipeline;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class SqliteCorpusEntryStoreBackendTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant-1", "test-corpus");

    @TempDir Path tempDir;
    private SqliteCorpusEntryStore store;
    private CorpusReader reader;
    private StubIngestor ingestor;

    @BeforeEach
    void setUp() {
        var ds = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        ingestor = new StubIngestor();
        var pipeline = new IngestionPipeline(ingestor);
        var context = new IngestionContext(CORPUS, null, null);

        CorpusStore backendStore = new FlatCorpusStore(tempDir);
        reader = (CorpusReader) backendStore;
        ContentSerializer serializer = new MarkdownContentSerializer();

        store = new SqliteCorpusEntryStore(ds, pipeline, context, backendStore, serializer);
    }

    @Test
    void addPersistsToBackend() {
        var entry = new CorpusEntry("doc.md", Map.of("title", "Test"), "Body text", null);
        store.add(CORPUS, entry);

        assertThat(reader.exists("doc.md")).isTrue();
        var content = new String(reader.read("doc.md").orElseThrow(), UTF_8);
        assertThat(content).contains("title: Test");
        assertThat(content).contains("Body text");
    }

    @Test
    void addPersistsToSqliteAndEmbeds() {
        var entry = new CorpusEntry("doc.md", Map.of("title", "Test"), "Body text", null);
        store.add(CORPUS, entry);

        assertThat(store.get(CORPUS, "doc.md")).isPresent();
        assertThat(ingestor.ingested).isNotEmpty();
        assertThat(ingestor.ingested.get(0).content()).isEqualTo("Body text");
    }

    @Test
    void updatePersistsNewVersionToBackend() {
        var entry = new CorpusEntry("doc.md", Map.of("title", "V1"), "Body v1", null);
        store.add(CORPUS, entry);

        var updated = new CorpusEntry("doc.md", Map.of("title", "V2"), "Body v2", null);
        store.update(CORPUS, "doc.md", updated);

        var content = new String(reader.read("doc.md").orElseThrow(), UTF_8);
        assertThat(content).contains("title: V2");
        assertThat(content).contains("Body v2");
    }

    @Test
    void removeDeletesFromBackend() {
        var entry = new CorpusEntry("doc.md", Map.of("title", "Test"), "Body", null);
        store.add(CORPUS, entry);
        assertThat(reader.exists("doc.md")).isTrue();

        store.remove(CORPUS, "doc.md");
        assertThat(reader.exists("doc.md")).isFalse();
    }

    @Test
    void addWithoutBackendStillWorks() {
        var ds = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        var pipeline = new IngestionPipeline(ingestor);
        var context = new IngestionContext(CORPUS, null, null);
        var storeNoBackend = new SqliteCorpusEntryStore(ds, pipeline, context, null, null);

        var entry = new CorpusEntry("doc.md", Map.of("title", "Test"), "Body", null);
        storeNoBackend.add(CORPUS, entry);

        assertThat(storeNoBackend.get(CORPUS, "doc.md")).isPresent();
    }

    static class StubIngestor implements EmbeddingIngestor {
        final List<ChunkInput> ingested = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();

        @Override
        public void ingest(CorpusRef corpus, List<ChunkInput> chunks) { ingested.addAll(chunks); }
        @Override
        public void deleteDocument(CorpusRef corpus, String sourceDocumentId) { deleted.add(sourceDocumentId); }
        @Override
        public void deleteCorpus(CorpusRef corpus) {}
        @Override
        public List<String> listDocuments(CorpusRef corpus) { return List.of(); }
    }
}

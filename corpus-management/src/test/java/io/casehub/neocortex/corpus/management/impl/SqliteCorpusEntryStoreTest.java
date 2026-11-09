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
        store = new SqliteCorpusEntryStore(ds, pipeline, context, null, null, null);
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

    @Test
    void recentChangesShouldReturnChangesAfterTimestamp() {
        var reader  = new StubCorpusReader();
        var oldTime = java.time.Instant.parse("2026-01-01T00:00:00Z");
        var newTime = java.time.Instant.parse("2026-11-01T00:00:00Z");
        reader.addEntry("old.md", oldTime, "Alice", "old content");
        reader.addEntry("new.md", newTime, "Bob", "new content");

        var ds              = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        var pipeline        = new IngestionPipeline(new StubIngestor());
        var context         = new IngestionContext(CORPUS, null, null);
        var storeWithReader = new SqliteCorpusEntryStore(ds, pipeline, context, null, null, reader);

        var since  = java.time.Instant.parse("2026-06-01T00:00:00Z");
        var result = storeWithReader.recentChanges(CORPUS, since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).path()).isEqualTo("new.md");
        assertThat(result.get(0).type()).isEqualTo(io.casehub.neocortex.corpus.ChangeType.ADDED);
        assertThat(result.get(0).author()).isEqualTo("Bob");
        assertThat(result.get(0).preview()).isEqualTo("new content");
    }

    @Test
    void recentChangesShouldReturnEmptyWhenNoChanges() {
        var reader = new StubCorpusReader();
        reader.addEntry("old.md", java.time.Instant.parse("2026-01-01T00:00:00Z"), null, "old");

        var ds              = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        var pipeline        = new IngestionPipeline(new StubIngestor());
        var context         = new IngestionContext(CORPUS, null, null);
        var storeWithReader = new SqliteCorpusEntryStore(ds, pipeline, context, null, null, reader);

        var future = java.time.Instant.parse("2027-01-01T00:00:00Z");
        assertThat(storeWithReader.recentChanges(CORPUS, future)).isEmpty();
    }

    @Test
    void recentChangesShouldDetectModification() {
        var reader  = new StubCorpusReader();
        var oldTime = java.time.Instant.parse("2026-01-01T00:00:00Z");
        var newTime = java.time.Instant.parse("2026-11-01T00:00:00Z");
        reader.addEntry("doc.md", oldTime, "Alice", "v1");
        reader.addEntry("doc.md", newTime, "Bob", "v2");

        var ds              = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        var pipeline        = new IngestionPipeline(new StubIngestor());
        var context         = new IngestionContext(CORPUS, null, null);
        var storeWithReader = new SqliteCorpusEntryStore(ds, pipeline, context, null, null, reader);

        var since  = java.time.Instant.parse("2026-06-01T00:00:00Z");
        var result = storeWithReader.recentChanges(CORPUS, since);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(io.casehub.neocortex.corpus.ChangeType.MODIFIED);
        assertThat(result.get(0).author()).isEqualTo("Bob");
    }

    @Test
    void versionsReturnsVersionInfoFromReader() {
        var reader = new StubCorpusReader();
        var t1     = java.time.Instant.parse("2026-01-01T00:00:00Z");
        var t2     = java.time.Instant.parse("2026-06-01T00:00:00Z");
        reader.addEntry("doc.md", t1, "Alice", "v1 content");
        reader.addEntry("doc.md", t2, "Bob", "v2 content");

        var ds              = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        var pipeline        = new IngestionPipeline(new StubIngestor());
        var context         = new IngestionContext(CORPUS, null, null);
        var storeWithReader = new SqliteCorpusEntryStore(ds, pipeline, context, null, null, reader);

        var versions = storeWithReader.versions(CORPUS, "doc.md");

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).version()).isEqualTo(1);
        assertThat(versions.get(0).author()).isEqualTo("Alice");
        assertThat(versions.get(1).version()).isEqualTo(2);
        assertThat(versions.get(1).author()).isEqualTo("Bob");
    }

    @Test
    void versionsReturnsEmptyWhenNoReader() {
        assertThat(store.versions(CORPUS, "doc.md")).isEmpty();
    }

    @Test
    void getVersionParsesContentToCorpusEntry() {
        var    reader    = new StubCorpusReader();
        var    t1        = java.time.Instant.parse("2026-01-01T00:00:00Z");
        String v1Content = "---\ntitle: Version One\ndomain: jvm\n---\n\nBody of version one.";
        reader.addEntry("doc.md", t1, "Alice", v1Content);

        var ds              = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        var pipeline        = new IngestionPipeline(new StubIngestor());
        var context         = new IngestionContext(CORPUS, null, null);
        var storeWithReader = new SqliteCorpusEntryStore(ds, pipeline, context, null, null, reader);

        var entry = storeWithReader.getVersion(CORPUS, "doc.md", 1);

        assertThat(entry).isPresent();
        assertThat(entry.get().id()).isEqualTo("doc.md");
        assertThat(entry.get().stringField("title")).isEqualTo("Version One");
        assertThat(entry.get().stringField("domain")).isEqualTo("jvm");
        assertThat(entry.get().body()).isEqualTo("Body of version one.");
    }

    @Test
    void getVersionReturnsEmptyWhenNoReader() {
        assertThat(store.getVersion(CORPUS, "doc.md", 1)).isEmpty();
    }

    @Test
    void getVersionReturnsEmptyForUnknownVersion() {
        var reader = new StubCorpusReader();
        reader.addEntry("doc.md", java.time.Instant.parse("2026-01-01T00:00:00Z"), null, "content");

        var ds              = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        var pipeline        = new IngestionPipeline(new StubIngestor());
        var context         = new IngestionContext(CORPUS, null, null);
        var storeWithReader = new SqliteCorpusEntryStore(ds, pipeline, context, null, null, reader);

        assertThat(storeWithReader.getVersion(CORPUS, "doc.md", 99)).isEmpty();
    }


    @Test
    void addUsesStructuralChunkerForPositionalMarkdown() {
        var ds = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        var structuralIngestor = new StubIngestor();
        var pipeline = new IngestionPipeline(structuralIngestor);
        var context = new IngestionContext(CORPUS, null, null);
        var chunker = new StubStructuralChunker();
        var structuralStore = new SqliteCorpusEntryStore(ds, pipeline, context, null, null, null, chunker);

        var entry = new CorpusEntry("e1",
                Map.of("title", "Test", "content_format", "positional-markdown"),
                "#@ page: 1\nSome content\n#@ page: 2\nMore content", null);
        structuralStore.add(CORPUS, entry);

        assertThat(chunker.called).isTrue();
        assertThat(structuralIngestor.ingested).hasSize(2);
        assertThat(structuralIngestor.ingested.get(0).content()).isEqualTo("chunk-1");
        assertThat(structuralIngestor.ingested.get(1).content()).isEqualTo("chunk-2");
    }

    @Test
    void addUsesDefaultPipelineForNonPositionalMarkdown() {
        var ds = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        var defaultIngestor = new StubIngestor();
        var pipeline = new IngestionPipeline(defaultIngestor);
        var context = new IngestionContext(CORPUS, null, null);
        var chunker = new StubStructuralChunker();
        var defaultStore = new SqliteCorpusEntryStore(ds, pipeline, context, null, null, null, chunker);

        var entry = new CorpusEntry("e1", Map.of("title", "Test"), "Plain body", null);
        defaultStore.add(CORPUS, entry);

        assertThat(chunker.called).isFalse();
        assertThat(defaultIngestor.ingested).hasSize(1);
        assertThat(defaultIngestor.ingested.get(0).content()).isEqualTo("Plain body");
    }

    // --- Stubs ---

    static class StubStructuralChunker implements io.casehub.neocortex.corpus.management.StructuralChunker {
        boolean called = false;

        @Override
        public List<io.casehub.neocortex.corpus.management.StructuredChunk> chunk(String positionalMarkdown, int maxChunkSize) {
            called = true;
            return List.of(
                    new io.casehub.neocortex.corpus.management.StructuredChunk("chunk-1", Map.of("page", "1")),
                    new io.casehub.neocortex.corpus.management.StructuredChunk("chunk-2", Map.of("page", "2")));
        }
    }


    @Test
    void addAnnotationReturnsId() {
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "Test"), "Body", null));
        String annId = store.addAnnotation(CORPUS, "e1", "Factual error in section 2", "correction", "agent-1");
        assertThat(annId).startsWith("ANN-");
    }

    @Test
    void annotationsAccumulate() {
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "Test"), "Body", null));
        store.addAnnotation(CORPUS, "e1", "First note", "correction", "agent-1");
        store.addAnnotation(CORPUS, "e1", "Second note", "caveat", "agent-2");

        var annotations = store.annotations(CORPUS, "e1");
        assertThat(annotations).hasSize(2);
        assertThat(annotations.get(0).note()).isEqualTo("First note");
        assertThat(annotations.get(0).annotationType()).isEqualTo("correction");
        assertThat(annotations.get(0).annotatedBy()).isEqualTo("agent-1");
        assertThat(annotations.get(1).note()).isEqualTo("Second note");
        assertThat(annotations.get(1).annotationType()).isEqualTo("caveat");
    }

    @Test
    void annotationsOrderedChronologically() {
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "Test"), "Body", null));
        store.addAnnotation(CORPUS, "e1", "First", "update", "agent-1");
        store.addAnnotation(CORPUS, "e1", "Second", "question", "agent-1");

        var annotations = store.annotations(CORPUS, "e1");
        assertThat(annotations.get(0).createdAt()).isBeforeOrEqualTo(annotations.get(1).createdAt());
    }

    @Test
    void annotationsIsolatedByEntry() {
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "A"), "Body A", null));
        store.add(CORPUS, new CorpusEntry("e2", Map.of("title", "B"), "Body B", null));
        store.addAnnotation(CORPUS, "e1", "Note on A", "correction", "agent-1");
        store.addAnnotation(CORPUS, "e2", "Note on B", "caveat", "agent-1");

        assertThat(store.annotations(CORPUS, "e1")).hasSize(1);
        assertThat(store.annotations(CORPUS, "e1").get(0).note()).isEqualTo("Note on A");
        assertThat(store.annotations(CORPUS, "e2")).hasSize(1);
        assertThat(store.annotations(CORPUS, "e2").get(0).note()).isEqualTo("Note on B");
    }

    @Test
    void annotationsIsolatedByCorpus() {
        var corpus2 = new CorpusRef("tenant-1", "other-corpus");
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "A"), "Body A", null));
        store.add(corpus2, new CorpusEntry("e1", Map.of("title", "B"), "Body B", null));
        store.addAnnotation(CORPUS, "e1", "Note in corpus 1", "correction", "agent-1");

        assertThat(store.annotations(CORPUS, "e1")).hasSize(1);
        assertThat(store.annotations(corpus2, "e1")).isEmpty();
    }

    @Test
    void annotationsReturnsEmptyForMissingEntry() {
        assertThat(store.annotations(CORPUS, "nonexistent")).isEmpty();
    }

    @Test
    void annotationCountsBatchQuery() {
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "A"), "Body", null));
        store.add(CORPUS, new CorpusEntry("e2", Map.of("title", "B"), "Body", null));
        store.add(CORPUS, new CorpusEntry("e3", Map.of("title", "C"), "Body", null));
        store.addAnnotation(CORPUS, "e1", "Note 1", "correction", "agent-1");
        store.addAnnotation(CORPUS, "e1", "Note 2", "caveat", "agent-1");
        store.addAnnotation(CORPUS, "e2", "Note 3", "update", "agent-2");

        var counts = store.annotationCounts(CORPUS, List.of("e1", "e2", "e3"));
        assertThat(counts).containsEntry("e1", 2);
        assertThat(counts).containsEntry("e2", 1);
        assertThat(counts).doesNotContainKey("e3");
    }

    @Test
    void annotationIncludesTimestampAndAnnotator() {
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "Test"), "Body", null));
        store.addAnnotation(CORPUS, "e1", "Note", "correction", "agent-1");

        var annotations = store.annotations(CORPUS, "e1");
        assertThat(annotations).hasSize(1);
        assertThat(annotations.get(0).annotatedBy()).isEqualTo("agent-1");
        assertThat(annotations.get(0).createdAt()).isNotNull();
        assertThat(annotations.get(0).entryId()).isEqualTo("e1");
        assertThat(annotations.get(0).id()).startsWith("ANN-");
    }


    @Test
    void addRelationshipReturnsId() {
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "A"), "Body", null));
        store.add(CORPUS, new CorpusEntry("e2", Map.of("title", "B"), "Body", null));
        String relId = store.addRelationship(CORPUS, "e1", "e2", "supersedes", "agent-1");
        assertThat(relId).startsWith("REL-");
    }

    @Test
    void relationshipsReturnsBidirectional() {
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "A"), "Body", null));
        store.add(CORPUS, new CorpusEntry("e2", Map.of("title", "B"), "Body", null));
        store.addRelationship(CORPUS, "e1", "e2", "supersedes", "agent-1");

        var fromE1 = store.relationships(CORPUS, "e1");
        assertThat(fromE1).hasSize(1);
        assertThat(fromE1.get(0).fromEntryId()).isEqualTo("e1");
        assertThat(fromE1.get(0).toEntryId()).isEqualTo("e2");

        var fromE2 = store.relationships(CORPUS, "e2");
        assertThat(fromE2).hasSize(1);
        assertThat(fromE2.get(0).fromEntryId()).isEqualTo("e1");
    }

    @Test
    void relationshipsAccumulate() {
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "A"), "Body", null));
        store.add(CORPUS, new CorpusEntry("e2", Map.of("title", "B"), "Body", null));
        store.add(CORPUS, new CorpusEntry("e3", Map.of("title", "C"), "Body", null));
        store.addRelationship(CORPUS, "e1", "e2", "supersedes", "agent-1");
        store.addRelationship(CORPUS, "e1", "e3", "see-also", "agent-1");

        assertThat(store.relationships(CORPUS, "e1")).hasSize(2);
    }

    @Test
    void relationshipsIsolatedByCorpus() {
        var corpus2 = new CorpusRef("tenant-1", "other-corpus");
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "A"), "Body", null));
        store.add(CORPUS, new CorpusEntry("e2", Map.of("title", "B"), "Body", null));
        store.add(corpus2, new CorpusEntry("e1", Map.of("title", "C"), "Body", null));
        store.add(corpus2, new CorpusEntry("e2", Map.of("title", "D"), "Body", null));
        store.addRelationship(CORPUS, "e1", "e2", "updates", "agent-1");

        assertThat(store.relationships(CORPUS, "e1")).hasSize(1);
        assertThat(store.relationships(corpus2, "e1")).isEmpty();
    }

    @Test
    void relationshipCountsBidirectional() {
        store.add(CORPUS, new CorpusEntry("e1", Map.of("title", "A"), "Body", null));
        store.add(CORPUS, new CorpusEntry("e2", Map.of("title", "B"), "Body", null));
        store.add(CORPUS, new CorpusEntry("e3", Map.of("title", "C"), "Body", null));
        store.addRelationship(CORPUS, "e1", "e2", "supersedes", "agent-1");
        store.addRelationship(CORPUS, "e3", "e1", "see-also", "agent-1");

        var counts = store.relationshipCounts(CORPUS, List.of("e1", "e2", "e3"));
        assertThat(counts).containsEntry("e1", 2);
        assertThat(counts).containsEntry("e2", 1);
        assertThat(counts).containsEntry("e3", 1);
    }

    @Test
    void relationshipsReturnsEmptyForMissingEntry() {
        assertThat(store.relationships(CORPUS, "nonexistent")).isEmpty();
    }

<<<<<<< HEAD

    @Test
    void addWithActorPopulatesAuditFields() {
        var entry = new CorpusEntry("e1", Map.of("title", "Test"), "Body", null);
        store.add(CORPUS, entry, "alice");

        var audit = store.auditInfo(CORPUS, "e1");
        assertThat(audit).isPresent();
        assertThat(audit.get().createdBy()).isEqualTo("alice");
        assertThat(audit.get().updatedBy()).isEqualTo("alice");
        assertThat(audit.get().createdAt()).isNotNull();
        assertThat(audit.get().updatedAt()).isNotNull();
    }

    @Test
    void updateWithActorSetsUpdatedBy() {
        var entry = new CorpusEntry("e1", Map.of("title", "Original"), "Body", null);
        store.add(CORPUS, entry, "alice");

        var updated = new CorpusEntry("e1", Map.of("title", "Updated"), "New body", null);
        store.update(CORPUS, "e1", updated, "bob");

        var audit = store.auditInfo(CORPUS, "e1");
        assertThat(audit).isPresent();
        assertThat(audit.get().createdBy()).isEqualTo("alice");
        assertThat(audit.get().updatedBy()).isEqualTo("bob");
    }

    @Test
    void addWithoutActorLeavesAuditFieldsNull() {
        var entry = new CorpusEntry("e1", Map.of("title", "Test"), "Body", null);
        store.add(CORPUS, entry);

        var audit = store.auditInfo(CORPUS, "e1");
        assertThat(audit).isPresent();
        assertThat(audit.get().createdBy()).isNull();
        assertThat(audit.get().updatedBy()).isNull();
        assertThat(audit.get().createdAt()).isNotNull();
        assertThat(audit.get().updatedAt()).isNotNull();
    }

    @Test
    void auditInfoReturnsEmptyForMissingEntry() {
        assertThat(store.auditInfo(CORPUS, "nonexistent")).isEmpty();
    }


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

    static class StubCorpusReader implements io.casehub.neocortex.corpus.CorpusReader {
        private final Map<String, List<io.casehub.neocortex.corpus.VersionInfo>> versionMap     = new java.util.LinkedHashMap<>();
        private final Map<String, byte[]>                                        content        = new java.util.LinkedHashMap<>();
        private final Map<String, Map<Integer, byte[]>>                          versionContent = new java.util.LinkedHashMap<>();

        void addEntry(String path, java.time.Instant timestamp, String author, String text) {
            var versions = versionMap.computeIfAbsent(path, k -> new ArrayList<>());
            int version  = versions.size() + 1;
            versions.add(new io.casehub.neocortex.corpus.VersionInfo(version, timestamp, "src-" + version, author));
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            content.put(path, bytes);
            versionContent.computeIfAbsent(path, k -> new java.util.LinkedHashMap<>()).put(version, bytes);
        }

        @Override
        public java.util.Optional<byte[]> read(String path) {
            return java.util.Optional.ofNullable(content.get(path));
        }

        @Override
        public java.util.Optional<java.io.InputStream> readStream(String path) {
            return read(path).map(java.io.ByteArrayInputStream::new);
        }

        @Override
        public java.util.Optional<byte[]> readVersion(String path, int version) {
            var perVersion = versionContent.get(path);
            if (perVersion == null) {return java.util.Optional.empty();}
            return java.util.Optional.ofNullable(perVersion.get(version));
        }

        @Override
        public List<io.casehub.neocortex.corpus.VersionInfo> versions(String path) {
            return versionMap.getOrDefault(path, List.of());
        }

        @Override
        public List<String> list() {return new ArrayList<>(versionMap.keySet());}

        @Override
        public List<String> list(String prefix) {
            return list().stream().filter(p -> p.startsWith(prefix)).toList();
        }

        @Override
        public boolean exists(String path) {return content.containsKey(path);}
    }

}

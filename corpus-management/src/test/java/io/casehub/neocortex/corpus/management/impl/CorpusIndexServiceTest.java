package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.rag.ChunkInput;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.rag.ExtractionResult;
import io.casehub.neocortex.rag.RetrievalOutcome;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievalRecord;
import io.casehub.neocortex.rag.RetrievalTracker;
import io.casehub.neocortex.rag.RetrievedDocumentRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusIndexServiceTest {

    private StubIngestor ingestor;
    private StubTracker tracker;
    private StubExtractor extractor;
    private CorpusIndexService service;
    private CorpusRef corpusRef;

    @BeforeEach
    void setUp() {
        ingestor = new StubIngestor();
        tracker = new StubTracker();
        extractor = new StubExtractor();
        corpusRef = new CorpusRef("test", "test-corpus");
        service = new CorpusIndexService(ingestor, tracker, extractor, null);
    }

    // --- status ---

    @Test
    void status_returns_entry_count() {
        ingestor.documents = List.of("domain/GE-20261001-abc123.md", "domain/GE-20261002-def456.md");
        CorpusStatus status = service.status(corpusRef, "/garden/path");
        assertThat(status.entryCount()).isEqualTo(2);
        assertThat(status.corpusId()).isEqualTo("test-corpus");
        assertThat(status.collectionReady()).isTrue();
    }

    @Test
    void status_returns_unavailable_when_ingestor_fails() {
        ingestor.throwOnList = true;
        CorpusStatus status = service.status(corpusRef, "/garden/path");
        assertThat(status.entryCount()).isEqualTo(-1);
        assertThat(status.collectionReady()).isFalse();
    }

    // --- deleteEntry ---

    @Test
    void deleteEntry_removes_vectors() {
        ingestor.documents = List.of("domain/GE-20261001-abc123.md");
        String result = service.deleteEntry(corpusRef, "GE-20261001-abc123");
        assertThat(result).contains("Deleted vectors");
        assertThat(ingestor.deletedDocId).isEqualTo("domain/GE-20261001-abc123.md");
    }

    @Test
    void deleteEntry_not_found() {
        ingestor.documents = List.of();
        String result = service.deleteEntry(corpusRef, "GE-20261001-missing");
        assertThat(result).contains("not found");
    }

    // --- entry ---

    @Test
    void entry_reads_file_and_extracts(@TempDir Path tempDir) throws IOException {
        String content = """
                ---
                title: Test Entry
                domain: jvm
                type: gotcha
                ---
                Body content here.
                """;
        Path entryFile = tempDir.resolve("GE-20261001-abc123.md");
        Files.writeString(entryFile, content);

        extractor.result = new ExtractionResult("Body content here.",
                Map.of("title", "Test Entry", "domain", "jvm", "type", "gotcha"),
                Map.of("see_also", List.of("GE-20261002-def456")));

        EntryDetail detail = service.entry(tempDir, "GE-20261001-abc123");
        assertThat(detail).isNotNull();
        assertThat(detail.id()).isEqualTo("GE-20261001-abc123");
        assertThat(detail.title()).isEqualTo("Test Entry");
        assertThat(detail.domain()).isEqualTo("jvm");
        assertThat(detail.body()).isEqualTo("Body content here.");
        assertThat(detail.seeAlsoIds()).containsExactly("GE-20261002-def456");
    }

    @Test
    void entry_returns_null_when_not_found(@TempDir Path tempDir) {
        EntryDetail detail = service.entry(tempDir, "GE-20261001-missing");
        assertThat(detail).isNull();
    }

    // --- reindexEntry ---

    @Test
    void reindexEntry_happy_path(@TempDir Path tempDir) throws IOException {
        String content = "---\ntitle: Test\ndomain: jvm\ntype: gotcha\n---\nBody.";
        Path entryFile = tempDir.resolve("GE-20261001-abc123.md");
        Files.writeString(entryFile, content);

        ingestor.documents = List.of("GE-20261001-abc123.md");
        extractor.result = new ExtractionResult("Body.",
                Map.of("title", "Test", "domain", "jvm", "type", "gotcha"));

        String result = service.reindexEntry(corpusRef, "GE-20261001-abc123", tempDir);
        assertThat(result).contains("Re-indexed");
        assertThat(ingestor.deletedDocId).isEqualTo("GE-20261001-abc123.md");
        assertThat(ingestor.ingestedChunks).hasSize(1);
    }

    @Test
    void reindexEntry_not_found(@TempDir Path tempDir) {
        ingestor.documents = List.of();
        String result = service.reindexEntry(corpusRef, "GE-missing", tempDir);
        assertThat(result).contains("not found");
    }

    @Test
    void reindexEntry_file_not_on_disk(@TempDir Path tempDir) {
        ingestor.documents = List.of("GE-20261001-abc123.md");
        String result = service.reindexEntry(corpusRef, "GE-20261001-abc123", tempDir);
        assertThat(result).contains("File not found on disk");
    }

    @Test
    void reindexEntry_empty_body(@TempDir Path tempDir) throws IOException {
        Path entryFile = tempDir.resolve("GE-20261001-abc123.md");
        Files.writeString(entryFile, "---\ntitle: Empty\n---\n");

        ingestor.documents = List.of("GE-20261001-abc123.md");
        extractor.result = new ExtractionResult("", Map.of("title", "Empty"));

        String result = service.reindexEntry(corpusRef, "GE-20261001-abc123", tempDir);
        assertThat(result).contains("no extractable content");
    }

    @Test
    void reindexEntry_resolves_from_filesystem(@TempDir Path tempDir) throws IOException {
        Path subdir = tempDir.resolve("jvm");
        Files.createDirectories(subdir);
        Path entryFile = subdir.resolve("GE-20261001-abc123.md");
        Files.writeString(entryFile, "---\ntitle: Sub\n---\nSubdir body.");

        ingestor.documents = List.of();
        extractor.result = new ExtractionResult("Subdir body.",
                Map.of("title", "Sub"));

        String result = service.reindexEntry(corpusRef, "GE-20261001-abc123", tempDir);
        assertThat(result).contains("Re-indexed");
    }

    // --- extractDocumentId ---

    @Test
    void extractDocumentId_strips_path_and_extension() {
        assertThat(CorpusIndexService.extractDocumentId("domain/GE-20261001-abc123.md"))
                .isEqualTo("GE-20261001-abc123");
    }

    @Test
    void extractDocumentId_non_ge_returns_full_path() {
        assertThat(CorpusIndexService.extractDocumentId("domain/some-file.md"))
                .isEqualTo("domain/some-file");
    }

    @Test
    void extractDocumentId_null_returns_empty() {
        assertThat(CorpusIndexService.extractDocumentId(null)).isEmpty();
    }

    // --- passesMinDaysFilter ---

    @Test
    void passesMinDaysFilter_recent_entry_filtered() {
        String todayId = "domain/GE-" + java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-abc123.md";
        assertThat(CorpusIndexService.passesMinDaysFilter(todayId, 30)).isFalse();
    }

    @Test
    void passesMinDaysFilter_old_entry_passes() {
        assertThat(CorpusIndexService.passesMinDaysFilter(
                "domain/GE-20240101-abc123.md", 30)).isTrue();
    }

    @Test
    void passesMinDaysFilter_non_ge_id_always_passes() {
        assertThat(CorpusIndexService.passesMinDaysFilter("domain/readme.md", 30)).isTrue();
    }

    // --- unretrieved ---

    @Test
    void unretrieved_reports_never_retrieved_documents() {
        ingestor.documents = List.of("domain/GE-20240101-aaa111.md", "domain/GE-20240102-bbb222.md");
        UnretrievedReport report = service.unretrieved(corpusRef, 0, 90);
        assertThat(report.neverRetrievedCount()).isEqualTo(2);
        assertThat(report.neverRetrieved()).hasSize(2);
    }

    @Test
    void unretrieved_filters_recent_by_minDays() {
        String todayDoc = "domain/GE-" + java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-abc123.md";
        ingestor.documents = List.of(todayDoc, "domain/GE-20240101-old111.md");
        UnretrievedReport report = service.unretrieved(corpusRef, 30, 90);
        assertThat(report.neverRetrievedCount()).isEqualTo(1);
        assertThat(report.neverRetrieved().get(0)).contains("old111");
    }

    @Test
    void unretrieved_empty_when_all_retrieved() {
        ingestor.documents = List.of();
        UnretrievedReport report = service.unretrieved(corpusRef, 0, 90);
        assertThat(report.neverRetrievedCount()).isZero();
        assertThat(report.staleCount()).isZero();
        assertThat(report.lowQualityCount()).isZero();
    }

    // --- recordFeedback ---

    @Test
    void recordFeedback_invalid_outcome() {
        String result = service.recordFeedback(corpusRef, List.of("GE-1"),
                "INVALID", null, null, null);
        assertThat(result).contains("Error: invalid outcome");
    }

    @Test
    void recordFeedback_outdated_requires_stack() {
        String result = service.recordFeedback(corpusRef, List.of("GE-1"),
                "OUTDATED", null, null, null);
        assertThat(result).contains("OUTDATED outcome requires a stack");
    }

    @Test
    void recordFeedback_records_for_known_entries() {
        ingestor.documents = List.of("domain/GE-20261001-abc123.md");
        tracker.records = List.of(new RetrievalRecord(
                "r-1", RetrievalQuery.of("test"), corpusRef,
                List.of(new RetrievedDocumentRef("domain/GE-20261001-abc123.md", 0.9)),
                10, Instant.now()));

        String result = service.recordFeedback(corpusRef,
                List.of("GE-20261001-abc123"), "RELEVANT", null, null, null);
        assertThat(result).contains("recorded: 1");
        assertThat(tracker.feedbackCount).isEqualTo(1);
    }

    @Test
    void recordFeedback_skips_unknown_entries() {
        ingestor.documents = List.of();
        String result = service.recordFeedback(corpusRef,
                List.of("GE-20261001-missing"), "RELEVANT", null, null, null);
        assertThat(result).contains("skipped");
    }

    // --- stubs ---

    static class StubIngestor implements EmbeddingIngestor {
        List<String> documents = List.of();
        boolean throwOnList;
        String deletedDocId;
        List<ChunkInput> ingestedChunks;

        @Override
        public void ingest(CorpusRef corpus, List<ChunkInput> chunks) {
            ingestedChunks = chunks;
        }

        @Override
        public void deleteDocument(CorpusRef corpus, String sourceDocumentId) {
            deletedDocId = sourceDocumentId;
        }

        @Override
        public void deleteCorpus(CorpusRef corpus) {}

        @Override
        public List<String> listDocuments(CorpusRef corpus) {
            if (throwOnList) throw new RuntimeException("Qdrant unavailable");
            return documents;
        }
    }

    static class StubTracker implements RetrievalTracker {
        List<RetrievalRecord> records = List.of();
        int feedbackCount;

        @Override
        public String record(io.casehub.neocortex.rag.RetrievalQuery query,
                             CorpusRef corpus,
                             List<io.casehub.neocortex.rag.RetrievedChunk> results,
                             int maxResults) {
            return "stub-retrieval-id";
        }

        @Override
        public void feedback(String retrievalId, String sourceDocumentId,
                             RetrievalOutcome outcome,
                             io.casehub.neocortex.rag.FeedbackContext context) {
            feedbackCount++;
        }

        @Override
        public List<RetrievalRecord> findRecords(CorpusRef corpus, Instant since, Instant until) {
            return records;
        }

        @Override
        public List<io.casehub.neocortex.rag.RetrievalFeedback> findFeedback(
                CorpusRef corpus, Instant since, Instant until) {
            return List.of();
        }

        @Override
        public java.util.Set<String> findRetrievedDocumentIds(
                CorpusRef corpus, Instant since, Instant until) {
            return java.util.Set.of();
        }

        @Override
        public int purgeOlderThan(Instant cutoff) {
            return 0;
        }
    }

    static class StubExtractor extends GardenMetadataExtractor {
        ExtractionResult result = new ExtractionResult("", Map.of());

        @Override
        public ExtractionResult extract(String path, byte[] content) {
            return result;
        }
    }
}

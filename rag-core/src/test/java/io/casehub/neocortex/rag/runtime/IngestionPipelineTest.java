package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.VersionInfo;
import io.casehub.neocortex.rag.ChunkInput;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.rag.ExtractionResult;
import io.casehub.neocortex.rag.MetadataExtractor;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class IngestionPipelineTest {

    @Test
    void ingestRawBytesExtractsMetadataAndEmbeds() {
        var capturedChunks = new ArrayList<List<ChunkInput>>();
        EmbeddingIngestor ingestor = stubIngestor(capturedChunks);
        MetadataExtractor extractor = (path, content) ->
                new ExtractionResult("extracted body", Map.of("title", "Test"));

        var pipeline = new IngestionPipeline(ingestor);
        var context = new IngestionContext(new CorpusRef("tenant", "corpus"), extractor, null);

        pipeline.ingest("doc.md", "raw content".getBytes(UTF_8), context);

        assertEquals(1, capturedChunks.size());
        assertEquals(1, capturedChunks.get(0).size());
        assertEquals("extracted body", capturedChunks.get(0).get(0).content());
        assertEquals("doc.md", capturedChunks.get(0).get(0).sourceDocumentId());
        assertEquals("Test", capturedChunks.get(0).get(0).metadata().get("title"));
    }

    @Test
    void ingestPreExtractedSkipsExtraction() {
        var capturedChunks = new ArrayList<List<ChunkInput>>();
        EmbeddingIngestor ingestor = stubIngestor(capturedChunks);

        var pipeline = new IngestionPipeline(ingestor);
        var context = new IngestionContext(new CorpusRef("tenant", "corpus"), null, null);
        var extracted = new ExtractionResult("pre-extracted", Map.of("key", "val"));

        pipeline.ingest("doc.md", extracted, context);

        assertEquals(1, capturedChunks.size());
        assertEquals("pre-extracted", capturedChunks.get(0).get(0).content());
    }

    @Test
    void ingestFromReaderReadsAndExtracts() {
        var capturedChunks = new ArrayList<List<ChunkInput>>();
        EmbeddingIngestor ingestor = stubIngestor(capturedChunks);
        MetadataExtractor extractor = (path, content) ->
                new ExtractionResult(new String(content, UTF_8), Map.of());

        CorpusReader reader = stubReader("doc.md", "from reader".getBytes(UTF_8));

        var pipeline = new IngestionPipeline(ingestor);
        var context = new IngestionContext(new CorpusRef("tenant", "corpus"), extractor, null);

        pipeline.ingest("doc.md", reader, context);

        assertEquals("from reader", capturedChunks.get(0).get(0).content());
    }

    @Test
    void ingestFromReaderSkipsMissingDocument() {
        var capturedChunks = new ArrayList<List<ChunkInput>>();
        EmbeddingIngestor ingestor = stubIngestor(capturedChunks);

        CorpusReader reader = emptyReader();

        var pipeline = new IngestionPipeline(ingestor);
        var context = new IngestionContext(new CorpusRef("tenant", "corpus"), null, null);

        pipeline.ingest("missing.md", reader, context);

        assertTrue(capturedChunks.isEmpty());
    }

    @Test
    void ingestBlankBodySkipsEmbedding() {
        var capturedChunks = new ArrayList<List<ChunkInput>>();
        EmbeddingIngestor ingestor = stubIngestor(capturedChunks);

        var pipeline = new IngestionPipeline(ingestor);
        var context = new IngestionContext(new CorpusRef("tenant", "corpus"), null, null);
        var extracted = new ExtractionResult("   ", Map.of());

        pipeline.ingest("doc.md", extracted, context);

        assertTrue(capturedChunks.isEmpty());
    }

    @Test
    void deleteCallsIngestorDeleteDocument() {
        var deleted = new ArrayList<String>();
        EmbeddingIngestor ingestor = new EmbeddingIngestor() {
            @Override public void ingest(CorpusRef corpus, List<ChunkInput> chunks) {}
            @Override public void deleteDocument(CorpusRef corpus, String documentId) { deleted.add(documentId); }
            @Override public void deleteCorpus(CorpusRef corpus) {}
            @Override public List<String> listDocuments(CorpusRef corpus) { return List.of(); }
        };

        var pipeline = new IngestionPipeline(ingestor);
        pipeline.delete("doc.md", new CorpusRef("tenant", "corpus"));

        assertEquals(List.of("doc.md"), deleted);
    }

    private static EmbeddingIngestor stubIngestor(List<List<ChunkInput>> capture) {
        return new EmbeddingIngestor() {
            @Override public void ingest(CorpusRef corpus, List<ChunkInput> chunks) { capture.add(chunks); }
            @Override public void deleteDocument(CorpusRef corpus, String documentId) {}
            @Override public void deleteCorpus(CorpusRef corpus) {}
            @Override public List<String> listDocuments(CorpusRef corpus) { return List.of(); }
        };
    }

    private static CorpusReader stubReader(String path, byte[] content) {
        return new CorpusReader() {
            @Override public Optional<byte[]> read(String p) { return p.equals(path) ? Optional.of(content) : Optional.empty(); }
            @Override public Optional<InputStream> readStream(String p) { return Optional.empty(); }
            @Override public Optional<byte[]> readVersion(String p, int v) { return Optional.empty(); }
            @Override public List<VersionInfo> versions(String p) { return List.of(); }
            @Override public List<String> list() { return List.of(path); }
            @Override public List<String> list(String prefix) { return List.of(); }
            @Override public boolean exists(String p) { return p.equals(path); }
        };
    }

    private static CorpusReader emptyReader() {
        return new CorpusReader() {
            @Override public Optional<byte[]> read(String p) { return Optional.empty(); }
            @Override public Optional<InputStream> readStream(String p) { return Optional.empty(); }
            @Override public Optional<byte[]> readVersion(String p, int v) { return Optional.empty(); }
            @Override public List<VersionInfo> versions(String p) { return List.of(); }
            @Override public List<String> list() { return List.of(); }
            @Override public List<String> list(String prefix) { return List.of(); }
            @Override public boolean exists(String p) { return false; }
        };
    }
}

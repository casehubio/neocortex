package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.rag.ChunkInput;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.rag.ExtractionResult;
import io.casehub.neocortex.rag.TextChunker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class IngestionPipeline {

    private final EmbeddingIngestor ingestor;

    public IngestionPipeline(EmbeddingIngestor ingestor) {
        this.ingestor = Objects.requireNonNull(ingestor);
    }

    public void ingest(String path, byte[] content, IngestionContext context) {
        Objects.requireNonNull(context.metadataExtractor(),
                "metadataExtractor required for raw-bytes ingestion");
        ExtractionResult extracted = context.metadataExtractor().extract(path, content);
        ingest(path, extracted, context);
    }

    public void ingest(String path, CorpusReader reader, IngestionContext context) {
        Optional<byte[]> content = reader.read(path);
        if (content.isEmpty()) return;
        ingest(path, content.get(), context);
    }

    public void ingest(String path, ExtractionResult extracted, IngestionContext context) {
        if (extracted.body() == null || extracted.body().isBlank()) return;
        List<ChunkInput> chunks = chunkDocument(path, extracted, context.textChunker());
        if (!chunks.isEmpty()) {
            ingestor.ingest(context.corpusRef(), chunks);
        }
    }

    public void delete(String path, CorpusRef corpusRef) {
        ingestor.deleteDocument(corpusRef, path);
    }

    List<ChunkInput> chunkDocument(String path, ExtractionResult result, TextChunker chunker) {
        if (result.body().isBlank()) {
            return List.of();
        }
        if (chunker == null) {
            return List.of(new ChunkInput(result.body(), path, result.metadata(), result.listMetadata()));
        }
        List<String> segments = chunker.chunk(result.body());
        List<ChunkInput> chunks = new ArrayList<>(segments.size());
        for (String segment : segments) {
            if (!segment.isBlank()) {
                chunks.add(new ChunkInput(segment, path, result.metadata(), result.listMetadata()));
            }
        }
        return chunks;
    }
}

package io.casehub.neocortex.rag.tika;

import io.casehub.neocortex.rag.TextChunker;
import io.casehub.neocortex.rag.ChunkInput;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TikaDocumentParser {

    private final int chunkSize;
    private final int chunkOverlap;
    private final TextChunker chunker;

    @Inject
    public TikaDocumentParser(TikaConfig config) {
        this(config.chunkSize(), config.chunkOverlap());
    }

    public TikaDocumentParser(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.chunker = TextChunker.recursive(chunkSize, chunkOverlap);
    }

    public List<ChunkInput> parse(InputStream content, String sourceDocumentId,
                                   String contentType, Map<String, String> metadata) {
        try {
            // Parse document with Tika
            BodyContentHandler handler = new BodyContentHandler(-1); // no limit
            org.apache.tika.metadata.Metadata tikaMetadata = new org.apache.tika.metadata.Metadata();
            if (contentType != null && !contentType.isBlank()) {
                tikaMetadata.set(org.apache.tika.metadata.Metadata.CONTENT_TYPE, contentType);
            }

            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(content, handler, tikaMetadata, new ParseContext());

            String text = handler.toString();

            // Extract Tika metadata
            Map<String, String> extractedMetadata = new HashMap<>();
            for (String name : tikaMetadata.names()) {
                String value = tikaMetadata.get(name);
                if (value != null && !value.isBlank()) {
                    extractedMetadata.put(name, value);
                }
            }

            // Chunk text
            List<String> segments = chunker.chunk(text);

            // Convert chunks to ChunkInput with merged metadata
            List<ChunkInput> chunks = new ArrayList<>(segments.size());
            for (String segment : segments) {
                Map<String, String> merged = new HashMap<>(metadata); // caller-provided metadata
                extractedMetadata.forEach(merged::putIfAbsent);
                chunks.add(new ChunkInput(segment, sourceDocumentId, merged));
            }
            return Collections.unmodifiableList(chunks);

        } catch (IOException | SAXException | TikaException e) {
            throw new RuntimeException("Failed to parse document with Tika: " + e.getMessage(), e);
        }
    }
}

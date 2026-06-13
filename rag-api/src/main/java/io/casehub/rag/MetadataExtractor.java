package io.casehub.rag;

public interface MetadataExtractor {
    ExtractionResult extract(String path, byte[] content);
}

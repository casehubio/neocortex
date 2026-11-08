package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.MetadataExtractor;
import io.casehub.neocortex.rag.TextChunker;

import java.util.Objects;

public record IngestionContext(
        CorpusRef corpusRef,
        MetadataExtractor metadataExtractor,
        TextChunker textChunker
) {
    public IngestionContext {
        Objects.requireNonNull(corpusRef, "corpusRef must not be null");
    }
}

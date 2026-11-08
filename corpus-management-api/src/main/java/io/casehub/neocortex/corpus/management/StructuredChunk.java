package io.casehub.neocortex.corpus.management;

import java.util.Map;

public record StructuredChunk(String text, Map<String, String> metadata) {
    public StructuredChunk {
        if (text == null) throw new IllegalArgumentException("text must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

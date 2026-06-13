package io.casehub.rag;

import java.util.Map;

public record ExtractionResult(String body, Map<String, String> metadata) {
    public ExtractionResult {
        if (body == null)
            throw new IllegalArgumentException("body must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

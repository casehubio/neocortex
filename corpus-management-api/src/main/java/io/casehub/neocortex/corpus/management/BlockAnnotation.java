package io.casehub.neocortex.corpus.management;

import java.util.Map;

public record BlockAnnotation(
    String type,
    String value,
    Map<String, String> params,
    int startOffset,
    int endOffset
) {
    public BlockAnnotation {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        if (value == null) value = "";
        params = params == null ? Map.of() : Map.copyOf(params);
        if (startOffset < 0) throw new IllegalArgumentException("startOffset must be >= 0");
        if (endOffset < startOffset) throw new IllegalArgumentException("endOffset must be >= startOffset");
    }
}

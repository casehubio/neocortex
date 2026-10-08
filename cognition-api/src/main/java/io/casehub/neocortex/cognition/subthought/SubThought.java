package io.casehub.neocortex.cognition.subthought;

public record SubThought(
    String type,
    String text,
    String entity,
    double confidence,
    Source source
) {
    public enum Source { SYNC, ASYNC }

    public SubThought {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type required");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text required");
        if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be [0, 1]");
        if (source == null) throw new IllegalArgumentException("source required");
    }
}

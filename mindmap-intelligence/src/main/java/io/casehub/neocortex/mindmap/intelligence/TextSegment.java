package io.casehub.neocortex.mindmap.intelligence;

public record TextSegment(String title, String body, String topic) {
    public TextSegment {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
        if (body == null) throw new NullPointerException("body");
    }
}

package io.casehub.neocortex.memory.experience;

import io.casehub.neocortex.memory.Memory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ScoreableContent(String text, Map<String, String> metadata, Instant timestamp) {
    public ScoreableContent {
        Objects.requireNonNull(text, "text required");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static ScoreableContent fromMemory(Memory memory) {
        return new ScoreableContent(
            memory.text(),
            memory.attributes(),
            memory.createdAt());
    }
}

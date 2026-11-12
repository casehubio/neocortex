package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.corpus.ChangeType;

import java.time.Instant;
import java.util.Objects;

public record ChangeRecord(
        String path,
        ChangeType type,
        Instant timestamp,
        String author,
        String preview) {

    public ChangeRecord {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}

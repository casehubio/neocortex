package io.casehub.neocortex.corpus;

import java.time.Instant;
import java.util.Objects;

public record AccessEvent(String path, String corpusName, Instant timestamp) {
    public AccessEvent {
        Objects.requireNonNull(path);
        Objects.requireNonNull(corpusName);
        Objects.requireNonNull(timestamp);
    }
}

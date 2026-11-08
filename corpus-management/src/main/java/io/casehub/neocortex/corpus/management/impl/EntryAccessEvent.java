package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.rag.CorpusRef;

import java.time.Instant;
import java.util.Objects;

public record EntryAccessEvent(CorpusRef corpus, String entryId, Instant timestamp) {
    public EntryAccessEvent {
        Objects.requireNonNull(corpus);
        Objects.requireNonNull(entryId);
        Objects.requireNonNull(timestamp);
    }
}

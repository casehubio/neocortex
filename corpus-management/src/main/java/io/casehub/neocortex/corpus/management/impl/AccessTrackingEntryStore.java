package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.rag.CorpusRef;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class AccessTrackingEntryStore implements CorpusEntryStore {

    private final CorpusEntryStore delegate;
    private final Consumer<EntryAccessEvent> eventSink;

    public AccessTrackingEntryStore(CorpusEntryStore delegate,
                                    Consumer<EntryAccessEvent> eventSink) {
        this.delegate = Objects.requireNonNull(delegate);
        this.eventSink = Objects.requireNonNull(eventSink);
    }

    @Override
    public String add(CorpusRef corpus, CorpusEntry entry) {
        return delegate.add(corpus, entry);
    }

    @Override
    public void update(CorpusRef corpus, String entryId, CorpusEntry entry) {
        delegate.update(corpus, entryId, entry);
    }

    @Override
    public void remove(CorpusRef corpus, String entryId) {
        delegate.remove(corpus, entryId);
    }

    @Override
    public Optional<CorpusEntry> get(CorpusRef corpus, String entryId) {
        Optional<CorpusEntry> result = delegate.get(corpus, entryId);
        if (result.isPresent()) {
            eventSink.accept(new EntryAccessEvent(corpus, entryId, Instant.now()));
        }
        return result;
    }
}

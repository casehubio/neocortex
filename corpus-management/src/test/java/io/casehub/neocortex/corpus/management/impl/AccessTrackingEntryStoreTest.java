package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTrackingEntryStoreTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant", "corpus");

    @Test
    void getEmitsAccessEvent() {
        var events = new ArrayList<EntryAccessEvent>();
        var entry = new CorpusEntry("doc-1", Map.of("title", "Test"), "Body", null);
        var delegate = stubStore(entry);
        var tracked = new AccessTrackingEntryStore(delegate, events::add);

        tracked.get(CORPUS, "doc-1");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).corpus()).isEqualTo(CORPUS);
        assertThat(events.get(0).entryId()).isEqualTo("doc-1");
        assertThat(events.get(0).timestamp()).isNotNull();
    }

    @Test
    void getMissDoesNotEmitEvent() {
        var events = new ArrayList<EntryAccessEvent>();
        var delegate = stubStore(null);
        var tracked = new AccessTrackingEntryStore(delegate, events::add);

        tracked.get(CORPUS, "missing");

        assertThat(events).isEmpty();
    }

    @Test
    void addDelegatesWithoutTracking() {
        var events = new ArrayList<EntryAccessEvent>();
        var delegate = stubStore(null);
        var tracked = new AccessTrackingEntryStore(delegate, events::add);

        var entry = new CorpusEntry("doc-1", Map.of(), "Body", null);
        tracked.add(CORPUS, entry);

        assertThat(events).isEmpty();
    }

    private static CorpusEntryStore stubStore(CorpusEntry returnOnGet) {
        return new CorpusEntryStore() {
            @Override public String add(CorpusRef c, CorpusEntry e) { return e.id(); }
            @Override public void update(CorpusRef c, String id, CorpusEntry e) {}
            @Override public void remove(CorpusRef c, String id) {}
            @Override public Optional<CorpusEntry> get(CorpusRef c, String id) {
                return Optional.ofNullable(returnOnGet);
            }
        };
    }
}

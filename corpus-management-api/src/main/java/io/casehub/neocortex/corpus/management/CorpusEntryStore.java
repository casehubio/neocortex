package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CorpusEntryStore {

    String add(CorpusRef corpus, CorpusEntry entry);

    void update(CorpusRef corpus, String entryId, CorpusEntry entry);

    void remove(CorpusRef corpus, String entryId);

    Optional<CorpusEntry> get(CorpusRef corpus, String entryId);

    default List<ChangeRecord> recentChanges(CorpusRef corpus, Instant since) {
        return List.of();
    }
}

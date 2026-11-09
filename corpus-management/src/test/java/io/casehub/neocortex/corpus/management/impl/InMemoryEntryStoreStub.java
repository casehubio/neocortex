package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.corpus.management.EntryRelationship;
import io.casehub.neocortex.rag.CorpusRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryEntryStoreStub implements CorpusEntryStore {

    final Map<String, CorpusEntry> entries = new ConcurrentHashMap<>();
    final List<EntryRelationship> relationships = new ArrayList<>();

    @Override
    public String add(CorpusRef corpus, CorpusEntry entry) {
        entries.put(entry.id(), entry);
        return entry.id();
    }

    @Override
    public String add(CorpusRef corpus, CorpusEntry entry, String actorId) {
        return add(corpus, entry);
    }

    @Override
    public void update(CorpusRef corpus, String entryId, CorpusEntry entry) {
        entries.put(entryId, entry);
    }

    @Override
    public void remove(CorpusRef corpus, String entryId) {
        entries.remove(entryId);
    }

    @Override
    public Optional<CorpusEntry> get(CorpusRef corpus, String entryId) {
        return Optional.ofNullable(entries.get(entryId));
    }

    @Override
    public List<CorpusEntry> findByMetadataField(CorpusRef corpus, String key, String value) {
        return entries.values().stream()
            .filter(e -> value.equals(e.stringField(key)))
            .toList();
    }

    @Override
    public String addRelationship(CorpusRef corpus, String fromEntryId, String toEntryId,
                                  String relationshipType, String createdBy) {
        String relId = "rel-" + relationships.size();
        relationships.add(new EntryRelationship(relId, fromEntryId, toEntryId,
            relationshipType, createdBy, Instant.now()));
        return relId;
    }
}

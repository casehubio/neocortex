package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.corpus.VersionInfo;
import io.casehub.neocortex.rag.CorpusRef;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CorpusEntryStore {

    String add(CorpusRef corpus, CorpusEntry entry);

    void update(CorpusRef corpus, String entryId, CorpusEntry entry);

    default String add(CorpusRef corpus, CorpusEntry entry, String actorId) {
        return add(corpus, entry);
    }

    default void update(CorpusRef corpus, String entryId, CorpusEntry entry, String actorId) {
        update(corpus, entryId, entry);
    }

    void remove(CorpusRef corpus, String entryId);

    Optional<CorpusEntry> get(CorpusRef corpus, String entryId);

    default Optional<AuditInfo> auditInfo(CorpusRef corpus, String entryId) {
        return Optional.empty();
    }

    default List<ChangeRecord> recentChanges(CorpusRef corpus, Instant since) {
        return List.of();
    }

    default List<VersionInfo> versions(CorpusRef corpus, String entryId) {
        return List.of();
    }

    default Optional<CorpusEntry> getVersion(CorpusRef corpus, String entryId, int version) {
        return Optional.empty();
    }

    default String addAnnotation(CorpusRef corpus, String entryId,
                                 String note, String annotationType, String annotatedBy) {
        throw new UnsupportedOperationException("Annotations not supported by this store");
    }

    default List<Annotation> annotations(CorpusRef corpus, String entryId) {
        return List.of();
    }

    default java.util.Map<String, Integer> annotationCounts(CorpusRef corpus, java.util.List<String> entryIds) {
        return java.util.Map.of();
    }

    default String addRelationship(CorpusRef corpus, String fromEntryId, String toEntryId,
                                   String relationshipType, String createdBy) {
        throw new UnsupportedOperationException("Relationships not supported by this store");
    }

    default List<EntryRelationship> relationships(CorpusRef corpus, String entryId) {
        return List.of();
    }

    default java.util.Map<String, Integer> relationshipCounts(CorpusRef corpus, java.util.List<String> entryIds) {
        return java.util.Map.of();
    }

    default List<CorpusEntry> findByMetadataField(CorpusRef corpus, String key, String value) {
        return List.of();
    }
}

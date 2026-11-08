package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.corpus.VersionInfo;
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


}

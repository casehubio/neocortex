package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusApiBeanTest {

    @Test
    void implements_corpus_api() {
        CorpusApiBean bean = createBean();
        assertThat(bean).isInstanceOf(CorpusApi.class);
    }

    @Test
    void profiles_delegates_to_store() {
        var store = new TestProfileStore();
        store.putDirect("test-profile", "quarkus:3.36.1|jdk:26");

        CorpusApiBean bean = new CorpusApiBean(null, null, null, store, null, null, null, null, null, null, null);
        assertThat(bean.profiles()).contains("test-profile");
        assertThat(bean.profile("test-profile")).isEqualTo("quarkus:3.36.1|jdk:26");
    }

    @Test
    void saveProfile_delegates() {
        var store = new TestProfileStore();
        CorpusApiBean bean = new CorpusApiBean(null, null, null, store, null, null, null, null, null, null, null);
        bean.saveProfile("new-profile", "jdk:21");
        assertThat(store.getRawStack("new-profile")).isEqualTo("jdk:21");
    }

    @Test
    void deleteProfile_delegates() {
        var store = new TestProfileStore();
        store.putDirect("doomed", "jdk:21");
        CorpusApiBean bean = new CorpusApiBean(null, null, null, store, null, null, null, null, null, null, null);
        assertThat(bean.deleteProfile("doomed")).isTrue();
        assertThat(bean.deleteProfile("nonexistent")).isFalse();
    }

    @Test
    void status_delegates_to_index_service() {
        var ingestor = new CorpusIndexServiceTest.StubIngestor();
        ingestor.documents = java.util.List.of("doc1", "doc2");
        var indexService = new CorpusIndexService(ingestor, null, null, null);
        var corpusRef = new CorpusRef("test", "test-corpus");

        CorpusApiBean bean = new CorpusApiBean(null, indexService, null, null, null, null, corpusRef, Path.of("/garden"), null, null, null);
        CorpusStatus status = bean.status("test-corpus");
        assertThat(status.entryCount()).isEqualTo(2);
        assertThat(status.collectionReady()).isTrue();
    }

    @Test
    void recordFeedback_splits_pipe_separated_ids() {
        var ingestor = new CorpusIndexServiceTest.StubIngestor();
        ingestor.documents = java.util.List.of();
        var tracker = new CorpusIndexServiceTest.StubTracker();
        var indexService = new CorpusIndexService(ingestor, tracker, null, null);
        var corpusRef = new CorpusRef("test", "test-corpus");

        CorpusApiBean bean = new CorpusApiBean(null, indexService, null, null, null, null, corpusRef, null, null, null, null);
        String result = bean.recordFeedback("GE-1|GE-2|GE-3", "RELEVANT", null, null, null);
        assertThat(result).contains("skipped");
    }

    @Test
    void recordFeedback_invalid_outcome_returns_error() {
        var ingestor = new CorpusIndexServiceTest.StubIngestor();
        var tracker = new CorpusIndexServiceTest.StubTracker();
        var indexService = new CorpusIndexService(ingestor, tracker, null, null);
        var corpusRef = new CorpusRef("test", "test-corpus");

        CorpusApiBean bean = new CorpusApiBean(null, indexService, null, null, null, null, corpusRef, null, null, null, null);
        String result = bean.recordFeedback("GE-1", "BOGUS", null, null, null);
        assertThat(result).contains("Error: invalid outcome");
    }

    // --- null guard tests (findings 2, 3) ---

    @Test
    void entry_returns_null_when_corpusRoot_is_null() {
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, null, null, null, null, null, null);
        assertThat(bean.entry("corpus", "GE-123")).isNull();
    }

    @Test
    void reindexEntry_returns_error_when_corpusRoot_is_null() {
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, null, null, null, null, null, null);
        assertThat(bean.reindexEntry("corpus", "GE-123")).contains("No corpus root");
    }

    @Test
    void recordProvenance_returns_zero_when_entryIds_null() {
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, null, null, null, null, null, null);
        assertThat(bean.recordProvenance("repo", 1, "spec", null, "test")).isZero();
    }

    @Test
    void recordProvenance_returns_zero_when_entryIds_blank() {
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, null, null, null, null, null, null);
        assertThat(bean.recordProvenance("repo", 1, "spec", "", "test")).isZero();
    }

    @Test
    void recordFeedback_returns_error_when_entryIds_null() {
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, null, null, null, null, null, null);
        assertThat(bean.recordFeedback(null, "RELEVANT", null, null, null)).contains("no entry IDs");
    }

    // --- reindex delegation ---

    @Test
    void reindex_delegates_to_reindex_service() {
        var reindexService = new CorpusReindexService(
                new CorpusIndexServiceTest.StubIngestor(),
                new CorpusRef("test", "test-corpus"),
                (ref, id) -> {});
        CorpusApiBean bean = new CorpusApiBean(null, null, reindexService, null, null, null, null, null, null, null, null);
        var result = bean.reindex("test-corpus");
        assertThat(result.status()).isEqualTo("ok");
    }

    // --- outcome delegation ---

    @Test
    void outcomeEntries_delegates() {
        var cbrStore = new io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrRecordStore();
        var outcomeService = new CorpusOutcomeService(cbrStore, "t1", "test", "corpus-outcome");
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, outcomeService, null, null, null, null, null);
        List<OutcomeEntry> entries = bean.outcomeEntries();
        assertThat(entries).isNotNull();
    }

    // --- deleteEntry delegation ---

    @Test
    void deleteEntry_delegates_to_index_service() {
        var ingestor = new CorpusIndexServiceTest.StubIngestor();
        ingestor.documents = java.util.List.of();
        var indexService = new CorpusIndexService(ingestor, null, null, null);
        var corpusRef = new CorpusRef("test", "test-corpus");

        CorpusApiBean bean = new CorpusApiBean(null, indexService, null, null, null, null, corpusRef, null, null, null, null);
        String result = bean.deleteEntry("test-corpus", "GE-missing");
        assertThat(result).contains("not found");
    }

    // --- unretrieved delegation ---

    @Test
    void unretrieved_delegates_with_defaults() {
        var ingestor = new CorpusIndexServiceTest.StubIngestor();
        ingestor.documents = java.util.List.of();
        var tracker = new CorpusIndexServiceTest.StubTracker();
        var indexService = new CorpusIndexService(ingestor, tracker, null, null);
        var corpusRef = new CorpusRef("test", "test-corpus");

        CorpusApiBean bean = new CorpusApiBean(null, indexService, null, null, null, null, corpusRef, null, null, null, null);
        UnretrievedReport report = bean.unretrieved("test-corpus", null, null);
        assertThat(report).isNotNull();
        assertThat(report.neverRetrievedCount()).isZero();
    }

    // --- annotation presence ---

    @Test
    void corpus_api_has_mcp_domain_annotation() {
        var annotation = CorpusApi.class.getAnnotation(
                io.casehub.platform.api.mcp.McpDomain.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("neocortex/corpus");
        assertThat(annotation.app()).isEqualTo("neocortex");
    }


// --- annotate validation ---

    @Test
    void annotate_rejects_null_entryId() {
        CorpusApiBean bean = createBean();
        assertThat(bean.annotate("corpus", null, "note", "correction")).contains("Error");
    }

    @Test
    void annotate_rejects_blank_note() {
        CorpusApiBean bean = createBean();
        assertThat(bean.annotate("corpus", "e1", "", "correction")).contains("Error");
    }

    @Test
    void annotate_rejects_invalid_type() {
        CorpusApiBean bean = createBean();
        assertThat(bean.annotate("corpus", "e1", "note", "invalid")).contains("Error");
    }

    @Test
    void annotate_accepts_valid_types() {
        for (String type : List.of("correction", "caveat", "update", "question")) {
            var           entryStore = new StubAnnotationStore();
            var           corpusRef  = new CorpusRef("test", "test-corpus");
            CorpusApiBean bean       = new CorpusApiBean(null, null, null, null, null, null, corpusRef, null, entryStore, null, null);
            String        result     = bean.annotate("corpus", "e1", "note", type);
            assertThat(result).startsWith("ANN-");
        }
    }

    @Test
    void annotate_normalises_type_to_lowercase() {
        var           entryStore = new StubAnnotationStore();
        var           corpusRef  = new CorpusRef("test", "test-corpus");
        CorpusApiBean bean       = new CorpusApiBean(null, null, null, null, null, null, corpusRef, null, entryStore, null, null);
        bean.annotate("corpus", "e1", "note", "CORRECTION");
        assertThat(entryStore.lastAnnotationType).isEqualTo("correction");
    }

    @Test
    void annotations_returns_empty_for_null_entryId() {
        CorpusApiBean bean = createBean();
        assertThat(bean.annotations("corpus", null)).isEmpty();
    }


// --- relationship validation ---

    @Test
    void relateEntries_rejects_null_fromEntryId() {
        CorpusApiBean bean = createBean();
        assertThat(bean.relateEntries("corpus", null, "e2", "supersedes")).contains("Error");
    }

    @Test
    void relateEntries_rejects_invalid_type() {
        CorpusApiBean bean = createBean();
        assertThat(bean.relateEntries("corpus", "e1", "e2", "invalid")).contains("Error");
    }

    @Test
    void relateEntries_accepts_valid_types() {
        for (String type : List.of("supersedes", "updates", "contradicts", "see-also", "derived-from")) {
            var           entryStore = new StubAnnotationStore();
            var           corpusRef  = new CorpusRef("test", "test-corpus");
            CorpusApiBean bean       = new CorpusApiBean(null, null, null, null, null, null, corpusRef, null, entryStore, null, null);
            String        result     = bean.relateEntries("corpus", "e1", "e2", type);
            assertThat(result).startsWith("REL-");
        }
    }

    @Test
    void relatedEntries_returns_empty_for_null_entryId() {
        CorpusApiBean bean = createBean();
        assertThat(bean.relatedEntries("corpus", null)).isEmpty();
    }

    @Test
    void relatedEntries_resolves_direction() {
        var           entryStore = new StubAnnotationStore();
        var           corpusRef  = new CorpusRef("test", "test-corpus");
        CorpusApiBean bean       = new CorpusApiBean(null, null, null, null, null, null, corpusRef, null, entryStore, null, null);

        entryStore.storedRelationships.add(new io.casehub.neocortex.corpus.management.EntryRelationship(
                "REL-1", "e1", "e2", "supersedes", "agent", java.time.Instant.now()));

        var fromE1 = bean.relatedEntries("corpus", "e1");
        assertThat(fromE1).hasSize(1);
        assertThat(fromE1.get(0).relationshipType()).isEqualTo("supersedes");
        assertThat(fromE1.get(0).direction()).isEqualTo("forward");

        var fromE2 = bean.relatedEntries("corpus", "e2");
        assertThat(fromE2).hasSize(1);
        assertThat(fromE2.get(0).relationshipType()).isEqualTo("superseded-by");
        assertThat(fromE2.get(0).direction()).isEqualTo("inverse");
    }

    static class StubAnnotationStore implements io.casehub.neocortex.corpus.management.CorpusEntryStore {
        String lastAnnotationType;
        final java.util.List<io.casehub.neocortex.corpus.management.EntryRelationship> storedRelationships = new java.util.ArrayList<>();

        @Override
        public String add(CorpusRef corpus, io.casehub.neocortex.corpus.management.CorpusEntry entry) {return entry.id();}

        @Override
        public void update(CorpusRef corpus, String entryId, io.casehub.neocortex.corpus.management.CorpusEntry entry) {}

        @Override
        public void remove(CorpusRef corpus, String entryId) {}

        @Override
        public java.util.Optional<io.casehub.neocortex.corpus.management.CorpusEntry> get(CorpusRef corpus, String entryId) {return java.util.Optional.empty();}

        @Override
        public String addAnnotation(CorpusRef corpus, String entryId, String note, String annotationType, String annotatedBy) {
            lastAnnotationType = annotationType;
            return "ANN-test-123";
        }

        @Override
        public List<io.casehub.neocortex.corpus.management.Annotation> annotations(CorpusRef corpus, String entryId) {return List.of();}

        @Override
        public String addRelationship(CorpusRef corpus, String fromEntryId, String toEntryId, String relationshipType, String createdBy) {
            storedRelationships.add(new io.casehub.neocortex.corpus.management.EntryRelationship(
                    "REL-test-123", fromEntryId, toEntryId, relationshipType, createdBy, java.time.Instant.now()));
            return "REL-test-123";
        }

        @Override
        public List<io.casehub.neocortex.corpus.management.EntryRelationship> relationships(CorpusRef corpus, String entryId) {
            return storedRelationships.stream()
                                      .filter(r -> r.fromEntryId().equals(entryId) || r.toEntryId().equals(entryId))
                                      .toList();
        }
    }

    private CorpusApiBean createBean() {
        return new CorpusApiBean(null, null, null, null, null, null, null, null, null, null, null);
    }

    static class TestProfileStore extends SearchProfileStore {
        private final java.util.Map<String, String> profiles = new java.util.LinkedHashMap<>();

        TestProfileStore() {
            super(io.casehub.neocortex.sqlite.SqliteDataSourceFactory
                    .create(":memory:", 1, 3000));
        }

        void putDirect(String name, String stack) {
            put(name, stack);
        }
    }
}

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

        CorpusApiBean bean = new CorpusApiBean(null, null, null, store, null, null, null, null, null);
        assertThat(bean.profiles()).contains("test-profile");
        assertThat(bean.profile("test-profile")).isEqualTo("quarkus:3.36.1|jdk:26");
    }

    @Test
    void saveProfile_delegates() {
        var store = new TestProfileStore();
        CorpusApiBean bean = new CorpusApiBean(null, null, null, store, null, null, null, null, null);
        bean.saveProfile("new-profile", "jdk:21");
        assertThat(store.getRawStack("new-profile")).isEqualTo("jdk:21");
    }

    @Test
    void deleteProfile_delegates() {
        var store = new TestProfileStore();
        store.putDirect("doomed", "jdk:21");
        CorpusApiBean bean = new CorpusApiBean(null, null, null, store, null, null, null, null, null);
        assertThat(bean.deleteProfile("doomed")).isTrue();
        assertThat(bean.deleteProfile("nonexistent")).isFalse();
    }

    @Test
    void status_delegates_to_index_service() {
        var ingestor = new CorpusIndexServiceTest.StubIngestor();
        ingestor.documents = java.util.List.of("doc1", "doc2");
        var indexService = new CorpusIndexService(ingestor, null, null, null);
        var corpusRef = new CorpusRef("test", "test-corpus");

        CorpusApiBean bean = new CorpusApiBean(null, indexService, null, null, null, null, corpusRef, Path.of("/garden"), null);
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

        CorpusApiBean bean = new CorpusApiBean(null, indexService, null, null, null, null, corpusRef, null, null);
        String result = bean.recordFeedback("GE-1|GE-2|GE-3", "RELEVANT", null, null, null);
        assertThat(result).contains("skipped");
    }

    @Test
    void recordFeedback_invalid_outcome_returns_error() {
        var ingestor = new CorpusIndexServiceTest.StubIngestor();
        var tracker = new CorpusIndexServiceTest.StubTracker();
        var indexService = new CorpusIndexService(ingestor, tracker, null, null);
        var corpusRef = new CorpusRef("test", "test-corpus");

        CorpusApiBean bean = new CorpusApiBean(null, indexService, null, null, null, null, corpusRef, null, null);
        String result = bean.recordFeedback("GE-1", "BOGUS", null, null, null);
        assertThat(result).contains("Error: invalid outcome");
    }

    // --- null guard tests (findings 2, 3) ---

    @Test
    void entry_returns_null_when_corpusRoot_is_null() {
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, null, null, null, null);
        assertThat(bean.entry("corpus", "GE-123")).isNull();
    }

    @Test
    void reindexEntry_returns_error_when_corpusRoot_is_null() {
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, null, null, null, null);
        assertThat(bean.reindexEntry("corpus", "GE-123")).contains("No corpus root");
    }

    @Test
    void recordProvenance_returns_zero_when_entryIds_null() {
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, null, null, null, null);
        assertThat(bean.recordProvenance("repo", 1, "spec", null, "test")).isZero();
    }

    @Test
    void recordProvenance_returns_zero_when_entryIds_blank() {
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, null, null, null, null);
        assertThat(bean.recordProvenance("repo", 1, "spec", "", "test")).isZero();
    }

    @Test
    void recordFeedback_returns_error_when_entryIds_null() {
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, null, null, null, null);
        assertThat(bean.recordFeedback(null, "RELEVANT", null, null, null)).contains("no entry IDs");
    }

    // --- reindex delegation ---

    @Test
    void reindex_delegates_to_reindex_service() {
        var reindexService = new GardenReindexService(
                new CorpusIndexServiceTest.StubIngestor(),
                new CorpusRef("test", "test-corpus"),
                (ref, id) -> {});
        CorpusApiBean bean = new CorpusApiBean(null, null, reindexService, null, null, null, null, null, null);
        var result = bean.reindex("test-corpus");
        assertThat(result.status()).isEqualTo("ok");
    }

    // --- outcome delegation ---

    @Test
    void outcomeEntries_delegates() {
        var cbrStore = new io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrRecordStore();
        var outcomeService = new CorpusOutcomeService(cbrStore, "t1", "test", "corpus-outcome");
        CorpusApiBean bean = new CorpusApiBean(null, null, null, null, null, outcomeService, null, null, null);
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

        CorpusApiBean bean = new CorpusApiBean(null, indexService, null, null, null, null, corpusRef, null, null);
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

        CorpusApiBean bean = new CorpusApiBean(null, indexService, null, null, null, null, corpusRef, null, null);
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

    private CorpusApiBean createBean() {
        return new CorpusApiBean(null, null, null, null, null, null, null, null, null);
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

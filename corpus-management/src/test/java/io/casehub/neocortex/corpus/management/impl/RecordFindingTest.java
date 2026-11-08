package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecordFindingTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant-1", "test-corpus");

    private RecordingEntryStore entryStore;
    private RecordingProvenanceStore provenanceStore;
    private CorpusApiBean bean;

    @BeforeEach
    void setUp() {
        entryStore = new RecordingEntryStore();
        provenanceStore = new RecordingProvenanceStore();
        bean = new CorpusApiBean(null, null, null, null,
                provenanceStore, null, CORPUS, null, entryStore);
    }

    @Test
    void recordFindingCreatesEntryWithMetadata() {
        String id = bean.recordFinding("test", "ONNX thread safety",
                "OrtSession is thread-safe but OrtEnvironment must be singleton",
                null, "java|onnx", "jvm", "finding");

        assertThat(id).startsWith("GE-");
        assertThat(entryStore.lastEntry).isNotNull();
        assertThat(entryStore.lastEntry.id()).isEqualTo(id);
        assertThat(entryStore.lastEntry.stringField("title")).isEqualTo("ONNX thread safety");
        assertThat(entryStore.lastEntry.stringField("domain")).isEqualTo("jvm");
        assertThat(entryStore.lastEntry.stringField("type")).isEqualTo("finding");
        assertThat(entryStore.lastEntry.body()).isEqualTo(
                "OrtSession is thread-safe but OrtEnvironment must be singleton");
    }

    @Test
    void recordFindingParsesTagsFromPipeDelimited() {
        bean.recordFinding("test", "Tag test", "insight", null, "java|onnx|quarkus", null, null);

        assertThat(entryStore.lastEntry.stringListField("tags"))
                .containsExactly("java", "onnx", "quarkus");
    }

    @Test
    void recordFindingParsesTagsFromCommaDelimited() {
        bean.recordFinding("test", "Tag test", "insight", null, "java,onnx,quarkus", null, null);

        assertThat(entryStore.lastEntry.stringListField("tags"))
                .containsExactly("java", "onnx", "quarkus");
    }

    @Test
    void recordFindingRecordsProvenanceWhenSourceIssueProvided() {
        bean.recordFinding("test", "Finding", "insight",
                "casehubio/neocortex#856", null, null, null);

        assertThat(provenanceStore.recorded).hasSize(1);
        assertThat(provenanceStore.recorded.get(0).issueRepo).isEqualTo("casehubio/neocortex");
        assertThat(provenanceStore.recorded.get(0).issueNumber).isEqualTo(856);
    }

    @Test
    void recordFindingSkipsProvenanceWhenNoSourceIssue() {
        bean.recordFinding("test", "Finding", "insight", null, null, null, null);

        assertThat(provenanceStore.recorded).isEmpty();
    }

    @Test
    void recordFindingRejectsBlankTopic() {
        String result = bean.recordFinding("test", "", "insight", null, null, null, null);

        assertThat(result).contains("Error");
        assertThat(entryStore.lastEntry).isNull();
    }

    @Test
    void recordFindingRejectsBlankInsight() {
        String result = bean.recordFinding("test", "Topic", "", null, null, null, null);

        assertThat(result).contains("Error");
        assertThat(entryStore.lastEntry).isNull();
    }

    @Test
    void recordFindingOmitsNullMetadataFields() {
        bean.recordFinding("test", "Topic only", "insight", null, null, null, null);

        assertThat(entryStore.lastEntry.metadata()).containsOnlyKeys("title");
    }

    @Test
    void recordFindingGeneratesUniqueIds() {
        String id1 = bean.recordFinding("test", "A", "a", null, null, null, null);
        String id2 = bean.recordFinding("test", "B", "b", null, null, null, null);

        assertThat(id1).isNotEqualTo(id2);
    }

    // --- Stubs ---

    static class RecordingEntryStore implements CorpusEntryStore {
        CorpusEntry lastEntry;

        @Override
        public String add(CorpusRef corpus, CorpusEntry entry) {
            lastEntry = entry;
            return entry.id();
        }

        @Override public void update(CorpusRef corpus, String entryId, CorpusEntry entry) {}
        @Override public void remove(CorpusRef corpus, String entryId) {}
        @Override public Optional<CorpusEntry> get(CorpusRef corpus, String entryId) {
            return Optional.empty();
        }
    }

    static class RecordingProvenanceStore extends ProvenanceStore {
        final List<ProvenanceCall> recorded = new ArrayList<>();

        RecordingProvenanceStore() {super(null);}

        @Override
        public int record(String issueRepo, int issueNumber, String specName,
                          List<String> documentIds, String recordedBy) {
            recorded.add(new ProvenanceCall(issueRepo, issueNumber, documentIds));
            return documentIds.size();
        }

        record ProvenanceCall(String issueRepo, int issueNumber, List<String> documentIds) {}
    }
}

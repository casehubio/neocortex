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

    @Test
    void recordFindingsStoresMultipleEntries() {
        var findings = List.of(
                new FindingInput("Topic A", "Insight A", null, List.of("java"), "jvm", "finding"),
                new FindingInput("Topic B", "Insight B", null, List.of("python"), "ml", "observation"));

        BatchFindingResult result = bean.recordFindings("test", findings);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.entryIds()).hasSize(2);
        assertThat(result.errors()).isEmpty();
        assertThat(entryStore.allEntries).hasSize(2);
        assertThat(entryStore.allEntries.get(0).stringField("title")).isEqualTo("Topic A");
        assertThat(entryStore.allEntries.get(1).stringField("title")).isEqualTo("Topic B");
    }

    @Test
    void recordFindingsHandlesPartialFailures() {
        var findings = List.of(
                new FindingInput("Valid", "insight", null, null, null, null),
                new FindingInput("", "insight", null, null, null, null),
                new FindingInput("Also valid", "another insight", null, null, null, null));

        BatchFindingResult result = bean.recordFindings("test", findings);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.entryIds()).hasSize(2);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("Finding 2");
    }

    @Test
    void recordFindingsRecordsProvenancePerFinding() {
        var findings = List.of(
                new FindingInput("A", "a", "casehubio/neocortex#100", null, null, null),
                new FindingInput("B", "b", "casehubio/neocortex#200", null, null, null),
                new FindingInput("C", "c", null, null, null, null));

        bean.recordFindings("test", findings);

        assertThat(provenanceStore.recorded).hasSize(2);
        assertThat(provenanceStore.recorded.get(0).issueNumber()).isEqualTo(100);
        assertThat(provenanceStore.recorded.get(1).issueNumber()).isEqualTo(200);
    }

    @Test
    void recordFindingsRejectsEmptyList() {
        BatchFindingResult result = bean.recordFindings("test", List.of());

        assertThat(result.total()).isZero();
        assertThat(result.succeeded()).isZero();
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    void recordFindingsRejectsNull() {
        BatchFindingResult result = bean.recordFindings("test", null);

        assertThat(result.total()).isZero();
        assertThat(result.succeeded()).isZero();
        assertThat(result.errors()).hasSize(1);
    }


    // --- Stubs ---

    static class RecordingEntryStore implements CorpusEntryStore {
        CorpusEntry lastEntry;
        final List<CorpusEntry> allEntries = new ArrayList<>();

        @Override
        public String add(CorpusRef corpus, CorpusEntry entry) {
            lastEntry = entry;
            allEntries.add(entry);
            return entry.id();
        }

        @Override
        public void update(CorpusRef corpus, String entryId, CorpusEntry entry) {}

        @Override
        public void remove(CorpusRef corpus, String entryId)                    {}

        @Override
        public Optional<CorpusEntry> get(CorpusRef corpus, String entryId) {
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

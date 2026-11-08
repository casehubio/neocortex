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
                provenanceStore, null, CORPUS, null, entryStore, null);
    }

    @Test
    void recordFindingCreatesEntryWithMetadata() {
        String id = bean.recordFinding("test", "ONNX thread safety",
                "OrtSession is thread-safe but OrtEnvironment must be singleton",
                null, "java|onnx", "jvm", "finding", null, null);

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
        bean.recordFinding("test", "Tag test", "insight", null, "java|onnx|quarkus", null, null, null, null);

        assertThat(entryStore.lastEntry.stringListField("tags"))
                .containsExactly("java", "onnx", "quarkus");
    }

    @Test
    void recordFindingParsesTagsFromCommaDelimited() {
        bean.recordFinding("test", "Tag test", "insight", null, "java,onnx,quarkus", null, null, null, null);

        assertThat(entryStore.lastEntry.stringListField("tags"))
                .containsExactly("java", "onnx", "quarkus");
    }

    @Test
    void recordFindingRecordsProvenanceWhenSourceIssueProvided() {
        bean.recordFinding("test", "Finding", "insight",
                "casehubio/neocortex#856", null, null, null, null, null);

        assertThat(provenanceStore.recorded).hasSize(1);
        assertThat(provenanceStore.recorded.get(0).issueRepo).isEqualTo("casehubio/neocortex");
        assertThat(provenanceStore.recorded.get(0).issueNumber).isEqualTo(856);
    }

    @Test
    void recordFindingSkipsProvenanceWhenNoSourceIssue() {
        bean.recordFinding("test", "Finding", "insight", null, null, null, null, null, null);

        assertThat(provenanceStore.recorded).isEmpty();
    }

    @Test
    void recordFindingRejectsBlankTopic() {
        String result = bean.recordFinding("test", "", "insight", null, null, null, null, null, null);

        assertThat(result).contains("Error");
        assertThat(entryStore.lastEntry).isNull();
    }

    @Test
    void recordFindingRejectsBlankInsight() {
        String result = bean.recordFinding("test", "Topic", "", null, null, null, null, null, null);

        assertThat(result).contains("Error");
        assertThat(entryStore.lastEntry).isNull();
    }

    @Test
    void recordFindingOmitsNullMetadataFields() {
        bean.recordFinding("test", "Topic only", "insight", null, null, null, null, null, null);

        assertThat(entryStore.lastEntry.metadata()).containsOnlyKeys("title", "status");
    }

    @Test
    void recordFindingGeneratesUniqueIds() {
        String id1 = bean.recordFinding("test", "A", "a", null, null, null, null, null, null);
        String id2 = bean.recordFinding("test", "B", "b", null, null, null, null, null, null);

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void recordFindingsStoresMultipleEntries() {
        var findings = List.of(
                new FindingInput("Topic A", "Insight A", null, List.of("java"), "jvm", "finding", null, null),
                new FindingInput("Topic B", "Insight B", null, List.of("python"), "ml", "observation", null, null));

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
                new FindingInput("Valid", "insight", null, null, null, null, null, null),
                new FindingInput("", "insight", null, null, null, null, null, null),
                new FindingInput("Also valid", "another insight", null, null, null, null, null, null));

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
                new FindingInput("A", "a", "casehubio/neocortex#100", null, null, null, null, null),
                new FindingInput("B", "b", "casehubio/neocortex#200", null, null, null, null, null),
                new FindingInput("C", "c", null, null, null, null, null, null));

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

    @Test
    void recordFindingDefaultsToPublished() {
        bean.recordFinding("test", "Topic", "insight", null, null, null, null, null, null);

        assertThat(entryStore.lastEntry.stringField("status")).isEqualTo("published");
    }

    @Test
    void recordFindingAcceptsDraftStatus() {
        bean.recordFinding("test", "Topic", "insight", null, null, null, null, "draft", null);

        assertThat(entryStore.lastEntry.stringField("status")).isEqualTo("draft");
    }

    @Test
    void publishEntryTransitionsDraftToPublished() {
        bean.recordFinding("test", "Topic", "insight", null, null, null, null, "draft", null);
        String entryId = entryStore.lastEntry.id();
        entryStore.stored.put(entryId, entryStore.lastEntry);

        String result = bean.publishEntry("test", entryId);

        assertThat(result).isEqualTo("draft → published");
        assertThat(entryStore.updated.get(entryId).stringField("status")).isEqualTo("published");
    }

    @Test
    void archiveEntryTransitionsToArchived() {
        bean.recordFinding("test", "Topic", "insight", null, null, null, null, null, null);
        String entryId = entryStore.lastEntry.id();
        entryStore.stored.put(entryId, entryStore.lastEntry);

        String result = bean.archiveEntry("test", entryId);

        assertThat(result).isEqualTo("published → archived");
        assertThat(entryStore.updated.get(entryId).stringField("status")).isEqualTo("archived");
    }

    @Test
    void publishEntryRejectsUnknownId() {
        String result = bean.publishEntry("test", "nonexistent");

        assertThat(result).contains("Error");
    }

    @Test
    void archiveEntryRejectsBlankId() {
        String result = bean.archiveEntry("test", "");

        assertThat(result).contains("Error");
    }

    @Test
    void buildStatusFilterExcludesDraftAndArchivedByDefault() {
        var filter = SearchResource.buildStatusFilter(null);
        assertThat(filter).isInstanceOf(io.casehub.neocortex.rag.PayloadFilter.Not.class);
    }

    @Test
    void buildStatusFilterReturnsNullForAll() {
        var filter = SearchResource.buildStatusFilter("all");
        assertThat(filter).isNull();
    }

    @Test
    void buildStatusFilterIncludesDraftWhenRequested() {
        var filter = SearchResource.buildStatusFilter("draft");
        assertThat(filter).isInstanceOf(io.casehub.neocortex.rag.PayloadFilter.Not.class);
        var not = (io.casehub.neocortex.rag.PayloadFilter.Not) filter;
        assertThat(not.inner()).isInstanceOf(io.casehub.neocortex.rag.PayloadFilter.Eq.class);
        assertThat(((io.casehub.neocortex.rag.PayloadFilter.Eq) not.inner()).value()).isEqualTo("archived");
    }

    @Test
    void contributionsTracksCreatedEntries() {
        bean.recordFinding("test", "Topic A", "insight a", null, null, null, null, null, null);
        bean.recordFinding("test", "Topic B", "insight b", null, null, null, null, null, null);

        List<ContributionRecord> contribs = bean.contributions("test", null, null);

        assertThat(contribs).hasSize(2);
        assertThat(contribs.get(0).action()).isEqualTo("created");
        assertThat(contribs.get(0).title()).isEqualTo("Topic A");
        assertThat(contribs.get(1).title()).isEqualTo("Topic B");
    }

    @Test
    void contributionsFiltersBySince() {
        bean.recordFinding("test", "Old", "old insight", null, null, null, null, null, null);
        java.time.Instant cutoff = java.time.Instant.now();
        bean.recordFinding("test", "New", "new insight", null, null, null, null, null, null);

        List<ContributionRecord> contribs = bean.contributions("test", cutoff, null);

        assertThat(contribs).hasSize(1);
        assertThat(contribs.get(0).title()).isEqualTo("New");
    }

    @Test
    void contributionsFiltersBySessionId() {
        bean.recordFinding("test", "Session A", "insight", null, null, null, null, null, "sess-1");
        bean.recordFinding("test", "Session B", "insight", null, null, null, null, null, "sess-2");
        bean.recordFinding("test", "No Session", "insight", null, null, null, null, null, null);

        List<ContributionRecord> contribs = bean.contributions("test", null, "sess-1");

        assertThat(contribs).hasSize(1);
        assertThat(contribs.get(0).title()).isEqualTo("Session A");
        assertThat(contribs.get(0).sessionId()).isEqualTo("sess-1");
    }

    @Test
    void contributionsTracksModifications() {
        bean.recordFinding("test", "Topic", "insight", null, null, null, null, null, null);
        String entryId = entryStore.lastEntry.id();
        entryStore.stored.put(entryId, entryStore.lastEntry);

        bean.archiveEntry("test", entryId);

        List<ContributionRecord> contribs = bean.contributions("test", null, null);
        assertThat(contribs).hasSize(2);
        assertThat(contribs.get(0).action()).isEqualTo("created");
        assertThat(contribs.get(1).action()).isEqualTo("modified");
    }


    // --- Stubs ---

    static class RecordingEntryStore implements CorpusEntryStore {
        CorpusEntry lastEntry;
        final List<CorpusEntry>                  allEntries = new ArrayList<>();
        final java.util.Map<String, CorpusEntry> stored     = new java.util.HashMap<>();
        final java.util.Map<String, CorpusEntry> updated    = new java.util.HashMap<>();

        @Override
        public String add(CorpusRef corpus, CorpusEntry entry) {
            lastEntry = entry;
            allEntries.add(entry);
            return entry.id();
        }

        @Override
        public void update(CorpusRef corpus, String entryId, CorpusEntry entry) {
            updated.put(entryId, entry);
        }

        @Override
        public void remove(CorpusRef corpus, String entryId) {}

        @Override
        public Optional<CorpusEntry> get(CorpusRef corpus, String entryId) {
            return Optional.ofNullable(stored.get(entryId));
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

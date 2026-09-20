package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CbrOutcomeData;
import io.casehub.neocortex.memory.cbr.CbrPath;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CbrOutcomeProcessorTest {

    @Test void onCbrOutcome_delegatesToStore() {
        var recorded = new ArrayList<RecordedOutcome>();
        var processor = new CbrOutcomeProcessor(new CapturingStore(recorded));

        var data = new CbrOutcomeData(
            "tenant-1", "case-42", CbrPath.FAULT,
            Map.of("node-a", "SUCCEEDED", "node-b", "FAILED"),
            1, 1, 2, 0.5,
            Instant.parse("2026-07-13T09:00:00Z"),
            Instant.parse("2026-07-13T10:00:00Z"));

        processor.onCbrOutcome(data);

        assertThat(recorded).hasSize(1);
        var r = recorded.getFirst();
        assertThat(r.caseId).isEqualTo("case-42");
        assertThat(r.tenantId).isEqualTo("tenant-1");
        assertThat(r.outcome.result()).isEqualTo(CbrOutcome.Outcome.PARTIAL);
        assertThat(r.outcome.successRate()).isEqualTo(0.5);
        assertThat(r.outcome.observedAt()).isEqualTo(Instant.parse("2026-07-13T10:00:00Z"));
        assertThat(r.outcome.detail()).contains("node-a");
    }

    @Test void onCbrOutcome_fullSuccess_mapsCorrectly() {
        var recorded = new ArrayList<RecordedOutcome>();
        var processor = new CbrOutcomeProcessor(new CapturingStore(recorded));

        var data = new CbrOutcomeData(
            "t1", "case-99", CbrPath.SITUATION,
            Map.of("n1", "SUCCEEDED"),
            1, 0, 1, 1.0,
            Instant.parse("2026-07-13T09:00:00Z"),
            Instant.parse("2026-07-13T10:00:00Z"));

        processor.onCbrOutcome(data);
        assertThat(recorded.getFirst().outcome.result()).isEqualTo(CbrOutcome.Outcome.SUCCESS);
    }

    @Test void onCbrOutcome_emptyNodeOutcomes_nullDetail() {
        var recorded = new ArrayList<RecordedOutcome>();
        var processor = new CbrOutcomeProcessor(new CapturingStore(recorded));

        var data = new CbrOutcomeData(
            "t1", "case-100", CbrPath.FAULT,
            Map.of(),
            0, 0, 0, 0.0,
            Instant.parse("2026-07-13T09:00:00Z"),
            Instant.parse("2026-07-13T10:00:00Z"));

        processor.onCbrOutcome(data);
        assertThat(recorded.getFirst().outcome.detail()).isNull();
    }

    record RecordedOutcome(String caseId, String tenantId, CbrOutcome outcome) {}

    static class CapturingStore implements CbrRecordStore {
        final List<RecordedOutcome> recorded;
        CapturingStore(List<RecordedOutcome> recorded) { this.recorded = recorded; }

        @Override public void recordOutcome(String caseId, String tenantId, CbrOutcome outcome) {
            recorded.add(new RecordedOutcome(caseId, tenantId, outcome));
        }
        @Override public void registerSchema(CbrRecordSchema s)                                                                                         {}
        @Override public String store(CbrRecord c, String t, String e, MemoryDomain d, String tid, String cid, io.casehub.platform.api.path.Path scope) { return ""; }
        @Override public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(CbrQuery q, Class<C> cl)                                               { return List.of(); }
        @Override public Integer erase(EraseRequest r)                                                                                                  { return 0; }
        @Override public Integer eraseEntity(String e, String t) { return 0; }
        @Override public Integer eraseByScope(io.casehub.platform.api.path.Path scope, String t) { return 0; }
        @Override public Integer purge(CbrRetentionPolicy p) { return 0; }
        @Override public boolean supersede(String c, String t, String s, String r) { return false; }
        @Override public boolean reinstate(String c, String t) { return false; }
        @Override public io.casehub.neocortex.memory.cbr.SupersessionStatus getSupersessionStatus(String caseId, String tenantId) { return io.casehub.neocortex.memory.cbr.SupersessionStatus.NOT_SUPERSEDED; }
        @Override public List<io.casehub.neocortex.memory.cbr.SupersessionStatus> findSupersededCases(String tenantId, MemoryDomain domain) { return List.of(); }
        @Override public List<String> findCaseIds(String t, MemoryDomain d, String ct, Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> f) { return List.of(); }
        @Override public int supersedeMatching(String t, MemoryDomain d, String ct, Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> f, String r) { return 0; }
        @Override public int supersedeAll(java.util.Collection<String> ids, String t, String r) { return 0; }
        @Override public int reinstateMatching(String t, MemoryDomain d, String ct, Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> f) { return 0; }
        @Override public int reinstateAll(java.util.Collection<String> ids, String t) { return 0; }
    }
}

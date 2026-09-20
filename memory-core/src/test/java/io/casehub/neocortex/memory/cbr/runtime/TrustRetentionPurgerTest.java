package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordSummary;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;
import io.casehub.neocortex.memory.cbr.CbrScanResult;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustRetentionPurgerTest {

    private static final MemoryDomain CBR = new MemoryDomain("cbr");
    private StubStore store;
    private StubTrustProvider trustProvider;

    @BeforeEach void setUp() {
        store = new StubStore();
        trustProvider = new StubTrustProvider();
    }

    private TrustRetentionPurger purger() {
        return new TrustRetentionPurger(store, trustProvider);
    }

    @Test void evaluateTenant_purgesCasesFromLowTrustAgents() {
        store.addCase("c1", "entity-1", "diagnosis", "agent-bad", 0.8, CBR, "t1");
        trustProvider.setTrust("agent-bad", 0.1);
        purger().evaluateTrajectories(true, Optional.of("cbr"), Optional.of(List.of("diagnosis")), 0.3);
        assertThat(store.erased).containsExactly("entity-1");
    }

    @Test void evaluateTenant_preservesCasesFromHighTrustAgents() {
        store.addCase("c1", "entity-1", "diagnosis", "agent-good", 0.9, CBR, "t1");
        trustProvider.setTrust("agent-good", 0.8);
        purger().evaluateTrajectories(true, Optional.of("cbr"), Optional.of(List.of("diagnosis")), 0.3);
        assertThat(store.erased).isEmpty();
    }

    @Test void evaluateTenant_skipsAgentsWithUnknownTrust() {
        store.addCase("c1", "entity-1", "diagnosis", "agent-x", 0.5, CBR, "t1");
        purger().evaluateTrajectories(true, Optional.of("cbr"), Optional.of(List.of("diagnosis")), 0.3);
        assertThat(store.erased).isEmpty();
    }

    @Test void evaluateTenant_paginatesCorrectly() {
        store.addCase("c1", "e1", "diagnosis", "agent-bad", 0.5, CBR, "t1");
        store.addCase("c2", "e2", "diagnosis", "agent-bad", 0.5, CBR, "t1");
        trustProvider.setTrust("agent-bad", 0.1);
        store.scanPageSize = 1;
        purger().evaluateTrajectories(true, Optional.of("cbr"), Optional.of(List.of("diagnosis")), 0.3);
        assertThat(store.erased).containsExactly("e1", "e2");
    }

    @Test void evaluateTenant_cachesAgentTrustLookups() {
        store.addCase("c1", "e1", "diagnosis", "agent-a", 0.5, CBR, "t1");
        store.addCase("c2", "e2", "diagnosis", "agent-a", 0.5, CBR, "t1");
        trustProvider.setTrust("agent-a", 0.8);
        purger().evaluateTrajectories(true, Optional.of("cbr"), Optional.of(List.of("diagnosis")), 0.3);
        assertThat(trustProvider.lookupCount).isEqualTo(1);
    }

    @Test void evaluateTrajectories_disabledSkipsExecution() {
        store.addCase("c1", "e1", "diagnosis", "agent-bad", 0.1, CBR, "t1");
        trustProvider.setTrust("agent-bad", 0.1);
        purger().evaluateTrajectories(false, Optional.of("cbr"), Optional.of(List.of("diagnosis")), 0.3);
        assertThat(store.erased).isEmpty();
        assertThat(store.scanCalled).isFalse();
    }

    @Test void evaluateTrajectories_unsupportedDiscoverTenants_logsAndReturns() {
        store.discoverTenantsUnsupported = true;
        purger().evaluateTrajectories(true, Optional.of("cbr"), Optional.of(List.of("diagnosis")), 0.3);
        assertThat(store.scanCalled).isFalse();
    }

    @Test void evaluateTrajectories_enabledWithEmptyDomain_throws() {
        assertThatThrownBy(() -> purger().evaluateTrajectories(true, Optional.empty(),
                Optional.of(List.of("diagnosis")), 0.3))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("domain");
    }

    @Test void evaluateTrajectories_enabledWithEmptyCaseTypes_throws() {
        assertThatThrownBy(() -> purger().evaluateTrajectories(true, Optional.of("cbr"),
                Optional.empty(), 0.3))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("case-types");
    }

    static class StubTrustProvider implements AgentTrustProvider {
        private final java.util.Map<String, Double> scores = new java.util.HashMap<>();
        int lookupCount = 0;
        void setTrust(String agentId, double score) { scores.put(agentId, score); }
        @Override public OptionalDouble currentTrustScore(String agentId) {
            lookupCount++;
            Double s = scores.get(agentId);
            return s != null ? OptionalDouble.of(s) : OptionalDouble.empty();
        }
    }

    static class StubStore implements CbrRecordStore {
        final List<CbrRecordSummary> cases  = new ArrayList<>();
        final List<String>           erased = new ArrayList<>();
        boolean scanCalled = false;
        boolean discoverTenantsUnsupported = false;
        int scanPageSize = 500;

        void addCase(String caseId, String entityId, String caseType,
                     String producerAgentId, Double trustScore,
                     MemoryDomain domain, String tenantId) {
            cases.add(new CbrRecordSummary(caseId, entityId, caseType, producerAgentId, trustScore, Instant.now()));
        }
        @Override public Set<String> discoverTenants(MemoryDomain domain) {
            if (discoverTenantsUnsupported) throw new UnsupportedOperationException("not supported");
            return Set.of("t1");
        }
        @Override public CbrScanResult scan(CbrScanRequest request) {
            scanCalled = true;
            boolean                pastCursor = request.cursor() == null;
            List<CbrRecordSummary> result     = new ArrayList<>();
            for (CbrRecordSummary c : cases) {
                if (!c.caseType().equals(request.caseType())) continue;
                if (!pastCursor) { if (c.caseId().equals(request.cursor())) pastCursor = true; continue; }
                result.add(c);
                if (result.size() >= scanPageSize) break;
            }
            String nextCursor = result.isEmpty() ? null : result.get(result.size() - 1).caseId();
            return new CbrScanResult(result, nextCursor);
        }
        @Override public Integer erase(EraseRequest request)                                                                                            { erased.add(request.entityId()); return 1; }
        @Override public void registerSchema(CbrRecordSchema s)                                                                                         {}
        @Override public String store(CbrRecord c, String t, String e, MemoryDomain d, String tid, String cid, io.casehub.platform.api.path.Path scope) { return ""; }
        @Override public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(CbrQuery q, Class<C> cl)                                               { return List.of(); }
        @Override public Integer eraseEntity(String e, String t)                                                                                        { return 0; }
        @Override public Integer eraseByScope(io.casehub.platform.api.path.Path scope, String t) { return 0; }
        @Override public void recordOutcome(String c, String t, CbrOutcome o) {}
        @Override public Integer purge(CbrRetentionPolicy p) { return 0; }
        @Override public boolean supersede(String c, String t, String s, String r) { return false; }
        @Override public boolean reinstate(String c, String t) { return false; }
        @Override public io.casehub.neocortex.memory.cbr.SupersessionStatus getSupersessionStatus(String caseId, String tenantId) { return io.casehub.neocortex.memory.cbr.SupersessionStatus.NOT_SUPERSEDED; }
        @Override public List<io.casehub.neocortex.memory.cbr.SupersessionStatus> findSupersededCases(String tenantId, MemoryDomain domain) { return List.of(); }
        @Override public List<String> findCaseIds(String t, MemoryDomain d, String ct, java.util.Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> f) { return List.of(); }
        @Override public int supersedeMatching(String t, MemoryDomain d, String ct, java.util.Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> f, String r) { return 0; }
        @Override public int supersedeAll(java.util.Collection<String> ids, String t, String r) { return 0; }
        @Override public int reinstateMatching(String t, MemoryDomain d, String ct, java.util.Map<String, io.casehub.neocortex.memory.cbr.CbrFilter> f) { return 0; }
        @Override public int reinstateAll(java.util.Collection<String> ids, String t) { return 0; }
    }
}

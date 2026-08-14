package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import io.casehub.neocortex.memory.cbr.CbrCaseSummary;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;
import io.casehub.neocortex.memory.cbr.CbrScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class TrustRetentionServiceTest {

    private static final MemoryDomain CBR = new MemoryDomain("cbr");

    private StubStore         store;
    private StubTrustProvider trustProvider;

    @BeforeEach
    void setUp() {
        store         = new StubStore();
        trustProvider = new StubTrustProvider();
    }

    private TrustRetentionService service(boolean enabled, double minTrust) {
        return new TrustRetentionService(store, trustProvider,
                                         new StubConfig(enabled, minTrust));
    }

    @Test
    void evaluateTenant_purgesCasesFromLowTrustAgents() {
        store.addCase("c1", "entity-1", "diagnosis", "agent-bad", 0.8, CBR, "t1");
        var svc = service(true, 0.3);
        trustProvider.setTrust("agent-bad", 0.1);
        svc.evaluateTrajectories();
        assertThat(store.erased).containsExactly("entity-1");
    }

    @Test
    void evaluateTenant_preservesCasesFromHighTrustAgents() {
        store.addCase("c1", "entity-1", "diagnosis", "agent-good", 0.9, CBR, "t1");
        var svc = service(true, 0.3);
        trustProvider.setTrust("agent-good", 0.8);
        svc.evaluateTrajectories();
        assertThat(store.erased).isEmpty();
    }

    @Test
    void evaluateTenant_skipsAgentsWithUnknownTrust() {
        store.addCase("c1", "entity-1", "diagnosis", "agent-x", 0.5, CBR, "t1");
        var svc = service(true, 0.3);
        svc.evaluateTrajectories();
        assertThat(store.erased).isEmpty();
    }

    @Test
    void evaluateTenant_paginatesCorrectly() {
        store.addCase("c1", "e1", "diagnosis", "agent-bad", 0.5, CBR, "t1");
        store.addCase("c2", "e2", "diagnosis", "agent-bad", 0.5, CBR, "t1");
        var svc = service(true, 0.3);
        trustProvider.setTrust("agent-bad", 0.1);
        store.scanPageSize = 1;
        svc.evaluateTrajectories();
        assertThat(store.erased).containsExactly("e1", "e2");
    }

    @Test
    void evaluateTenant_cachesAgentTrustLookups() {
        store.addCase("c1", "e1", "diagnosis", "agent-a", 0.5, CBR, "t1");
        store.addCase("c2", "e2", "diagnosis", "agent-a", 0.5, CBR, "t1");
        var svc = service(true, 0.3);
        trustProvider.setTrust("agent-a", 0.8);
        svc.evaluateTrajectories();
        assertThat(trustProvider.lookupCount).isEqualTo(1);
    }

    @Test
    void evaluateTrajectories_disabledSkipsExecution() {
        store.addCase("c1", "e1", "diagnosis", "agent-bad", 0.1, CBR, "t1");
        var svc = service(false, 0.3);
        trustProvider.setTrust("agent-bad", 0.1);
        svc.evaluateTrajectories();
        assertThat(store.erased).isEmpty();
        assertThat(store.scanCalled).isFalse();
    }

    @Test
    void evaluateTrajectories_unsupportedDiscoverTenants_logsAndReturns() {
        store.discoverTenantsUnsupported = true;
        var svc = service(true, 0.3);
        svc.evaluateTrajectories();
        assertThat(store.scanCalled).isFalse();
    }

    @Test
    void evaluateTrajectories_enabledWithEmptyDomain_throws() {
        var svc = new TrustRetentionService(store, trustProvider,
                                            new StubConfig(true, 0.3) {
                                                @Override
                                                public Optional<String> domain() {return Optional.empty();}
                                            });
        org.assertj.core.api.Assertions.assertThatThrownBy(svc::evaluateTrajectories)
                                       .isInstanceOf(IllegalStateException.class)
                                       .hasMessageContaining("domain");
    }

    @Test
    void evaluateTrajectories_enabledWithEmptyCaseTypes_throws() {
        var svc = new TrustRetentionService(store, trustProvider,
                                            new StubConfig(true, 0.3) {
                                                @Override
                                                public Optional<List<String>> caseTypes() {return Optional.empty();}
                                            });
        org.assertj.core.api.Assertions.assertThatThrownBy(svc::evaluateTrajectories)
                                       .isInstanceOf(IllegalStateException.class)
                                       .hasMessageContaining("case-types");
    }


    // --- stubs ---

    static class StubConfig implements TrustRetentionConfig {
        final boolean enabled;
        final double  minCurrentTrust;

        StubConfig(boolean enabled, double minCurrentTrust) {
            this.enabled         = enabled;
            this.minCurrentTrust = minCurrentTrust;
        }

        @Override
        public boolean enabled()        {return enabled;}

        @Override
        public String interval()        {return "24h";}

        @Override
        public double minCurrentTrust() {return minCurrentTrust;}

        @Override
        public Optional<String> domain() {return Optional.of("cbr");}

        @Override
        public Optional<List<String>> caseTypes() {return Optional.of(List.of("diagnosis"));}
    }

    static class StubTrustProvider implements AgentTrustProvider {
        private final java.util.Map<String, Double> scores = new java.util.HashMap<>();
        int lookupCount = 0;

        void setTrust(String agentId, double score) {scores.put(agentId, score);}

        @Override
        public OptionalDouble currentTrustScore(String agentId) {
            lookupCount++;
            Double s = scores.get(agentId);
            return s != null ? OptionalDouble.of(s) : OptionalDouble.empty();
        }
    }

    static class StubStore extends NoOpCbrCaseMemoryStore {
        final List<CbrCaseSummary> cases  = new java.util.ArrayList<>();
        final List<String>         erased = new java.util.ArrayList<>();
        boolean scanCalled                 = false;
        boolean discoverTenantsUnsupported = false;
        int     scanPageSize               = 500;

        void addCase(String caseId, String entityId, String caseType,
                     String producerAgentId, Double trustScore,
                     MemoryDomain domain, String tenantId) {
            cases.add(new CbrCaseSummary(caseId, entityId, caseType,
                                         producerAgentId, trustScore, Instant.now()));
        }

        @Override
        public java.util.Set<String> discoverTenants(MemoryDomain domain) {
            if (discoverTenantsUnsupported) {throw new UnsupportedOperationException("not supported");}
            return java.util.Set.of("t1");
        }

        @Override
        public CbrScanResult scan(CbrScanRequest request) {
            scanCalled = true;
            boolean              pastCursor = request.cursor() == null;
            List<CbrCaseSummary> result     = new java.util.ArrayList<>();
            for (CbrCaseSummary c : cases) {
                if (!c.caseType().equals(request.caseType())) {continue;}
                if (!pastCursor) {
                    if (c.caseId().equals(request.cursor())) {pastCursor = true;}
                    continue;
                }
                result.add(c);
                if (result.size() >= scanPageSize) {break;}
            }
            String nextCursor = result.isEmpty() ? null : result.get(result.size() - 1).caseId();
            return new CbrScanResult(result, nextCursor);
        }

        @Override
        public Integer erase(EraseRequest request) {
            erased.add(request.entityId());
            return 1;
        }
    }
}

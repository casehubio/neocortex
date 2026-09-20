package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.EraseRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CbrRetentionPurgerTest {

    private static final MemoryDomain CBR = new MemoryDomain("cbr");

    @Test void purgeExpired_constructsPolicyPerTenantAndCaseType() {
        var store = new CapturingStore();
        var purger = new CbrRetentionPurger(store);
        purger.purgeExpired(true, Optional.of("cbr"),
                Optional.of(List.of("diagnosis", "treatment")),
                Optional.of(365), Optional.of(10000), Optional.of(0.2));
        assertThat(store.policies).hasSize(2);
        var diag = store.policies.stream().filter(p -> p.caseType().equals("diagnosis")).findFirst().orElseThrow();
        assertThat(diag.tenantId()).isEqualTo("t1");
        assertThat(diag.maxAgeDays()).isEqualTo(365);
        assertThat(diag.maxCasesPerType()).isEqualTo(10000);
        assertThat(diag.minTrustScore()).isEqualTo(0.2);
        assertThat(store.policies.stream().anyMatch(p -> p.caseType().equals("treatment"))).isTrue();
    }

    @Test void purgeExpired_disabledSkipsExecution() {
        var store = new CapturingStore();
        var purger = new CbrRetentionPurger(store);
        purger.purgeExpired(false, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        assertThat(store.policies).isEmpty();
        assertThat(store.discoverTenantsCalled).isFalse();
    }

    @Test void purgeExpired_unsupportedDiscoverTenants_logsAndReturns() {
        var store = new CapturingStore();
        store.discoverTenantsUnsupported = true;
        var purger = new CbrRetentionPurger(store);
        purger.purgeExpired(true, Optional.of("cbr"),
                Optional.of(List.of("diagnosis", "treatment")),
                Optional.of(30), Optional.empty(), Optional.empty());
        assertThat(store.policies).isEmpty();
    }

    @Test void purgeExpired_continuesOnPerTenantFailure() {
        var store = new CapturingStore();
        store.tenants = Set.of("t1", "t2");
        store.failForTenant = "t1";
        var purger = new CbrRetentionPurger(store);
        purger.purgeExpired(true, Optional.of("cbr"),
                Optional.of(List.of("diagnosis", "treatment")),
                Optional.of(30), Optional.empty(), Optional.empty());
        assertThat(store.policies.stream().anyMatch(p -> p.tenantId().equals("t2"))).isTrue();
    }

    @Test void purgeExpired_enabledWithEmptyDomain_throws() {
        var store = new CapturingStore();
        var purger = new CbrRetentionPurger(store);
        assertThatThrownBy(() -> purger.purgeExpired(true, Optional.empty(),
                Optional.of(List.of("diagnosis")), Optional.empty(), Optional.empty(), Optional.empty()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("domain");
    }

    @Test void purgeExpired_enabledWithEmptyCaseTypes_throws() {
        var store = new CapturingStore();
        var purger = new CbrRetentionPurger(store);
        assertThatThrownBy(() -> purger.purgeExpired(true, Optional.of("cbr"),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("case-types");
    }

    static class CapturingStore implements CbrRecordStore {
        final List<CbrRetentionPolicy> policies = new ArrayList<>();
        boolean discoverTenantsCalled = false;
        boolean discoverTenantsUnsupported = false;
        Set<String> tenants = Set.of("t1");
        String failForTenant = null;

        @Override public java.util.Set<String> discoverTenants(MemoryDomain domain) {
            discoverTenantsCalled = true;
            if (discoverTenantsUnsupported) throw new UnsupportedOperationException("not supported");
            return tenants;
        }
        @Override public Integer purge(CbrRetentionPolicy policy) {
            if (policy.tenantId().equals(failForTenant)) throw new RuntimeException("db error");
            policies.add(policy);
            return 0;
        }
        @Override public void registerSchema(CbrRecordSchema s)                                                                                         {}
        @Override public String store(CbrRecord c, String t, String e, MemoryDomain d, String tid, String cid, io.casehub.platform.api.path.Path scope) { return ""; }
        @Override public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(CbrQuery q, Class<C> cl)                                               { return List.of(); }
        @Override public Integer erase(EraseRequest r)                                                                                                  { return 0; }
        @Override public Integer eraseEntity(String e, String t) { return 0; }
        @Override public Integer eraseByScope(io.casehub.platform.api.path.Path scope, String t) { return 0; }
        @Override public void recordOutcome(String c, String t, CbrOutcome o) {}
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

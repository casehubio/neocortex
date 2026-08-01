package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CbrRetentionSchedulerTest {

    private static final MemoryDomain CBR = new MemoryDomain("cbr");

    @Test
    void purgeExpired_constructsPolicyPerTenantAndCaseType() {
        var store     = new CapturingStore();
        var config    = new StubConfig(true, Optional.of(365), Optional.of(10000), Optional.of(0.2));
        var scheduler = new CbrRetentionScheduler(store, config);
        scheduler.purgeExpired();
        assertThat(store.policies).hasSize(2);
        var diag = store.policies.stream().filter(p -> p.caseType().equals("diagnosis")).findFirst().orElseThrow();
        assertThat(diag.tenantId()).isEqualTo("t1");
        assertThat(diag.maxAgeDays()).isEqualTo(365);
        assertThat(diag.maxCasesPerType()).isEqualTo(10000);
        assertThat(diag.minTrustScore()).isEqualTo(0.2);
        assertThat(store.policies.stream().anyMatch(p -> p.caseType().equals("treatment"))).isTrue();
    }

    @Test
    void purgeExpired_disabledSkipsExecution() {
        var store     = new CapturingStore();
        var config    = new StubConfig(false, Optional.empty(), Optional.empty(), Optional.empty());
        var scheduler = new CbrRetentionScheduler(store, config);
        scheduler.purgeExpired();
        assertThat(store.policies).isEmpty();
        assertThat(store.discoverTenantsCalled).isFalse();
    }

    @Test
    void purgeExpired_unsupportedDiscoverTenants_logsAndReturns() {
        var store = new CapturingStore();
        store.discoverTenantsUnsupported = true;
        var config    = new StubConfig(true, Optional.of(30), Optional.empty(), Optional.empty());
        var scheduler = new CbrRetentionScheduler(store, config);
        scheduler.purgeExpired();
        assertThat(store.policies).isEmpty();
    }

    @Test
    void purgeExpired_continuesOnPerTenantFailure() {
        var store = new CapturingStore();
        store.tenants       = java.util.Set.of("t1", "t2");
        store.failForTenant = "t1";
        var config    = new StubConfig(true, Optional.of(30), Optional.empty(), Optional.empty());
        var scheduler = new CbrRetentionScheduler(store, config);
        scheduler.purgeExpired();
        assertThat(store.policies.stream().anyMatch(p -> p.tenantId().equals("t2"))).isTrue();
    }

    // --- stubs ---

    static class StubConfig implements CbrRetentionConfig {
        final boolean           enabled;
        final Optional<Integer> maxAgeDays;
        final Optional<Integer> maxCasesPerType;
        final Optional<Double>  minTrustScore;

        StubConfig(boolean enabled, Optional<Integer> maxAgeDays,
                   Optional<Integer> maxCasesPerType, Optional<Double> minTrustScore) {
            this.enabled         = enabled;
            this.maxAgeDays      = maxAgeDays;
            this.maxCasesPerType = maxCasesPerType;
            this.minTrustScore   = minTrustScore;
        }

        @Override
        public boolean enabled()                   {return enabled;}

        @Override
        public String interval()                   {return "24h";}

        @Override
        public String domain()                     {return "cbr";}

        @Override
        public java.util.List<String> caseTypes()  {return java.util.List.of("diagnosis", "treatment");}

        @Override
        public Optional<Integer> maxAgeDays()      {return maxAgeDays;}

        @Override
        public Optional<Integer> maxCasesPerType() {return maxCasesPerType;}

        @Override
        public Optional<Double> minTrustScore()    {return minTrustScore;}
    }

    static class CapturingStore extends NoOpCbrCaseMemoryStore {
        final java.util.List<CbrRetentionPolicy> policies = new java.util.ArrayList<>();
        boolean               discoverTenantsCalled      = false;
        boolean               discoverTenantsUnsupported = false;
        java.util.Set<String> tenants                    = java.util.Set.of("t1");
        String                failForTenant              = null;

        @Override
        public java.util.Set<String> discoverTenants(MemoryDomain domain) {
            discoverTenantsCalled = true;
            if (discoverTenantsUnsupported) {throw new UnsupportedOperationException("not supported");}
            return tenants;
        }

        @Override
        public Integer purge(CbrRetentionPolicy policy) {
            if (policy.tenantId().equals(failForTenant)) {throw new RuntimeException("db error");}
            policies.add(policy);
            return 0;
        }
    }
}

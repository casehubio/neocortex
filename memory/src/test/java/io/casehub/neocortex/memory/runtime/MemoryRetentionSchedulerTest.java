package io.casehub.neocortex.memory.runtime;

import io.casehub.neocortex.memory.MemoryCapability;
import io.casehub.neocortex.memory.MemoryRetentionPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryRetentionSchedulerTest {

    @Test void purgeExpired_constructsPolicyPerTenant() {
        var store = new CapturingStore();
        var config = new StubConfig(true, Optional.of(180), Optional.of(0.2));
        var scheduler = new MemoryRetentionScheduler(store, config);
        scheduler.purgeExpired();
        assertThat(store.policies).hasSize(2);
        assertThat(store.policies.stream().anyMatch(p -> p.tenantId().equals("t1"))).isTrue();
        assertThat(store.policies.stream().anyMatch(p -> p.tenantId().equals("t2"))).isTrue();
        assertThat(store.policies.getFirst().maxAgeDays()).isEqualTo(180);
        assertThat(store.policies.getFirst().minImportance()).isEqualTo(0.2);
    }

    @Test void purgeExpired_disabledSkipsExecution() {
        var store = new CapturingStore();
        var config = new StubConfig(false, Optional.empty(), Optional.empty());
        var scheduler = new MemoryRetentionScheduler(store, config);
        scheduler.purgeExpired();
        assertThat(store.policies).isEmpty();
    }

    @Test void purgeExpired_checksBothCapabilities() {
        var store = new NoPurgeStore();
        var config = new StubConfig(true, Optional.of(30), Optional.empty());
        var scheduler = new MemoryRetentionScheduler(store, config);
        scheduler.purgeExpired();
        assertThat(store.purgeCalled).isFalse();
    }

    @Test void purgeExpired_continuesOnPerTenantFailure() {
        var store = new CapturingStore();
        store.failForTenant = "t1";
        var config = new StubConfig(true, Optional.of(30), Optional.empty());
        var scheduler = new MemoryRetentionScheduler(store, config);
        scheduler.purgeExpired();
        assertThat(store.policies.stream().anyMatch(p -> p.tenantId().equals("t2"))).isTrue();
    }

    @Test
    void purgeExpired_enabledWithEmptyDomain_throws() {
        var store = new CapturingStore();
        var config = new StubConfig(true, Optional.of(30), Optional.empty()) {
            @Override
            public String domain() {return "";}
        };
        var scheduler = new MemoryRetentionScheduler(store, config);
        org.assertj.core.api.Assertions.assertThatThrownBy(scheduler::purgeExpired)
                                       .isInstanceOf(IllegalStateException.class)
                                       .hasMessageContaining("domain");
    }


    static class StubConfig implements MemoryRetentionConfig {
        final boolean enabled;
        final Optional<Integer> maxAgeDays;
        final Optional<Double> minImportance;
        StubConfig(boolean enabled, Optional<Integer> maxAgeDays, Optional<Double> minImportance) {
            this.enabled = enabled;
            this.maxAgeDays = maxAgeDays;
            this.minImportance = minImportance;
        }
        @Override public boolean enabled() { return enabled; }
        @Override public String domain() { return "MEMORY"; }
        @Override public Optional<Integer> maxAgeDays() { return maxAgeDays; }
        @Override public Optional<Double> minImportance() { return minImportance; }
    }

    static class CapturingStore extends NoOpCaseMemoryStore {
        final List<MemoryRetentionPolicy> policies = new ArrayList<>();
        String failForTenant = null;
        @Override public Set<MemoryCapability> capabilities() {
            return Set.of(MemoryCapability.DISCOVER_TENANTS, MemoryCapability.PURGE);
        }
        @Override public Set<String> discoverTenants(String k, String v) { return Set.of("t1", "t2"); }
        @Override public int purge(MemoryRetentionPolicy policy) {
            if (policy.tenantId().equals(failForTenant)) throw new RuntimeException("db error");
            policies.add(policy);
            return 0;
        }
    }

    static class NoPurgeStore extends NoOpCaseMemoryStore {
        boolean purgeCalled = false;
        @Override public Set<MemoryCapability> capabilities() {
            return Set.of(MemoryCapability.DISCOVER_TENANTS);
        }
        @Override public int purge(MemoryRetentionPolicy policy) {
            purgeCalled = true;
            return 0;
        }
    }
}

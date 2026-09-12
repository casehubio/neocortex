package io.casehub.neocortex.memory.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryCapability;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.MemoryRetentionPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryRetentionPurgerTest {

    @Test void purgeExpired_constructsPolicyPerTenant() {
        var store = new CapturingStore();
        var purger = new MemoryRetentionPurger(store);
        purger.purgeExpired(true, Optional.of("MEMORY"), Optional.of(180), Optional.of(0.2));
        assertThat(store.policies).hasSize(2);
        assertThat(store.policies.stream().anyMatch(p -> p.tenantId().equals("t1"))).isTrue();
        assertThat(store.policies.stream().anyMatch(p -> p.tenantId().equals("t2"))).isTrue();
        assertThat(store.policies.getFirst().maxAgeDays()).isEqualTo(180);
        assertThat(store.policies.getFirst().minConfidence()).isEqualTo(0.2);
    }

    @Test void purgeExpired_disabledSkipsExecution() {
        var store = new CapturingStore();
        var purger = new MemoryRetentionPurger(store);
        purger.purgeExpired(false, Optional.empty(), Optional.empty(), Optional.empty());
        assertThat(store.policies).isEmpty();
    }

    @Test void purgeExpired_checksBothCapabilities() {
        var store = new NoPurgeStore();
        var purger = new MemoryRetentionPurger(store);
        purger.purgeExpired(true, Optional.of("MEMORY"), Optional.of(30), Optional.empty());
        assertThat(store.purgeCalled).isFalse();
    }

    @Test void purgeExpired_continuesOnPerTenantFailure() {
        var store = new CapturingStore();
        store.failForTenant = "t1";
        var purger = new MemoryRetentionPurger(store);
        purger.purgeExpired(true, Optional.of("MEMORY"), Optional.of(30), Optional.empty());
        assertThat(store.policies.stream().anyMatch(p -> p.tenantId().equals("t2"))).isTrue();
    }

    @Test void purgeExpired_enabledWithEmptyDomain_throws() {
        var store = new CapturingStore();
        var purger = new MemoryRetentionPurger(store);
        assertThatThrownBy(() -> purger.purgeExpired(true, Optional.empty(), Optional.of(30), Optional.empty()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("domain");
    }

    static class CapturingStore implements CaseMemoryStore {
        final List<MemoryRetentionPolicy> policies = new ArrayList<>();
        String failForTenant = null;
        @Override public Set<MemoryCapability> capabilities() { return Set.of(MemoryCapability.DISCOVER_TENANTS, MemoryCapability.PURGE); }
        @Override public Set<String> discoverTenants(String k, String v) { return Set.of("t1", "t2"); }
        @Override public int purge(MemoryRetentionPolicy policy) {
            if (policy.tenantId().equals(failForTenant)) throw new RuntimeException("db error");
            policies.add(policy);
            return 0;
        }
        @Override public String store(MemoryInput input) { return ""; }
        @Override public List<Memory> query(MemoryQuery query) { return List.of(); }
        @Override public int erase(EraseRequest request) { return 0; }
    }

    static class NoPurgeStore implements CaseMemoryStore {
        boolean purgeCalled = false;
        @Override public Set<MemoryCapability> capabilities() { return Set.of(MemoryCapability.DISCOVER_TENANTS); }
        @Override public int purge(MemoryRetentionPolicy policy) { purgeCalled = true; return 0; }
        @Override public String store(MemoryInput input) { return ""; }
        @Override public List<Memory> query(MemoryQuery query) { return List.of(); }
        @Override public int erase(EraseRequest request) { return 0; }
    }
}

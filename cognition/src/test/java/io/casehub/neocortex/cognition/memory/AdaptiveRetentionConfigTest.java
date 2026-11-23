package io.casehub.neocortex.cognition.memory;

import java.util.Set;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveRetentionConfigTest {

    @Test
    void startsWithDefault() {
        var holder = new AdaptiveRetentionConfig();
        assertThat(holder.current()).isEqualTo(RetentionConfig.DEFAULT);
    }

    @Test
    void updateSwapsConfig() {
        var holder = new AdaptiveRetentionConfig();
        var custom = new RetentionConfig(0.2, 1.0, 1.0, 0.5, 0.5);
        holder.update(custom);
        assertThat(holder.current()).isEqualTo(custom);
    }

    @Test
    void updateIsVisibleAcrossReads() {
        var holder = new AdaptiveRetentionConfig();
        var c1 = new RetentionConfig(0.15, 1.0, 1.0, 0.5, 0.5);
        var c2 = new RetentionConfig(0.25, 1.0, 1.0, 0.5, 0.5);
        holder.update(c1);
        assertThat(holder.current().retentionThreshold()).isEqualTo(0.15);
        holder.update(c2);
        assertThat(holder.current().retentionThreshold()).isEqualTo(0.25);
    }

    @Test
    void perTenantConfigIsIsolated() {
        var holder   = new AdaptiveRetentionConfig();
        var t1Config = new RetentionConfig(0.2, 1.0, 1.0, 0.5, 0.5);
        var t2Config = new RetentionConfig(0.3, 1.0, 1.0, 0.5, 0.5);
        holder.update("t1", t1Config);
        holder.update("t2", t2Config);
        assertThat(holder.current("t1")).isEqualTo(t1Config);
        assertThat(holder.current("t2")).isEqualTo(t2Config);
    }

    @Test
    void perTenantFallsBackToGlobalWhenNotSet() {
        var holder = new AdaptiveRetentionConfig();
        var global = new RetentionConfig(0.15, 1.0, 1.0, 0.5, 0.5);
        holder.update(global);
        assertThat(holder.current("unknown-tenant")).isEqualTo(global);
    }

    @Test
    void perTenantDoesNotAffectGlobal() {
        var holder = new AdaptiveRetentionConfig();
        holder.update("t1", new RetentionConfig(0.4, 1.0, 1.0, 0.5, 0.5));
        assertThat(holder.current()).isEqualTo(RetentionConfig.DEFAULT);
    }

    @Test
    void evictAbsentRemovesUnknownTenants() {
        var holder = new AdaptiveRetentionConfig();
        holder.update("t1", new RetentionConfig(0.2, 1.0, 1.0, 0.5, 0.5));
        holder.update("t2", new RetentionConfig(0.3, 1.0, 1.0, 0.5, 0.5));
        holder.update("t3", new RetentionConfig(0.4, 1.0, 1.0, 0.5, 0.5));
        holder.evictAbsent(Set.of("t1", "t3"));
        assertThat(holder.current("t1").retentionThreshold()).isEqualTo(0.2);
        assertThat(holder.current("t2")).isEqualTo(RetentionConfig.DEFAULT);
        assertThat(holder.current("t3").retentionThreshold()).isEqualTo(0.4);
    }

    @Test
    void perAgentConfigIsIsolated() {
        var holder   = new AdaptiveRetentionConfig();
        var a1Config = new RetentionConfig(0.2, 1.0, 1.0, 0.5, 0.5);
        var a2Config = new RetentionConfig(0.3, 1.0, 1.0, 0.5, 0.5);
        holder.update("t1", "agent-a", a1Config);
        holder.update("t1", "agent-b", a2Config);
        assertThat(holder.current("t1", "agent-a")).isEqualTo(a1Config);
        assertThat(holder.current("t1", "agent-b")).isEqualTo(a2Config);
    }

    @Test
    void perAgentFallsBackToTenantConfig() {
        var holder       = new AdaptiveRetentionConfig();
        var tenantConfig = new RetentionConfig(0.25, 1.0, 1.0, 0.5, 0.5);
        holder.update("t1", tenantConfig);
        assertThat(holder.current("t1", "unknown-agent")).isEqualTo(tenantConfig);
    }

    @Test
    void perAgentFallsBackToGlobalWhenNoTenantConfig() {
        var holder = new AdaptiveRetentionConfig();
        var global = new RetentionConfig(0.15, 1.0, 1.0, 0.5, 0.5);
        holder.update(global);
        assertThat(holder.current("unknown-tenant", "unknown-agent")).isEqualTo(global);
    }

    @Test
    void perAgentDoesNotAffectTenantConfig() {
        var holder       = new AdaptiveRetentionConfig();
        var tenantConfig = new RetentionConfig(0.2, 1.0, 1.0, 0.5, 0.5);
        holder.update("t1", tenantConfig);
        holder.update("t1", "agent-a", new RetentionConfig(0.4, 1.0, 1.0, 0.5, 0.5));
        assertThat(holder.current("t1")).isEqualTo(tenantConfig);
    }

    @Test
    void evictAbsentRemovesAgentConfigsForEvictedTenants() {
        var holder = new AdaptiveRetentionConfig();
        holder.update("t1", "agent-a", new RetentionConfig(0.2, 1.0, 1.0, 0.5, 0.5));
        holder.update("t2", "agent-b", new RetentionConfig(0.3, 1.0, 1.0, 0.5, 0.5));
        holder.evictAbsent(Set.of("t1"));
        assertThat(holder.current("t1", "agent-a").retentionThreshold()).isEqualTo(0.2);
        assertThat(holder.current("t2", "agent-b")).isEqualTo(RetentionConfig.DEFAULT);
    }
}

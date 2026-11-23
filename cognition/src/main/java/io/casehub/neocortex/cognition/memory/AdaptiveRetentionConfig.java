package io.casehub.neocortex.cognition.memory;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class AdaptiveRetentionConfig implements RetentionConfigSupplier {
    private final AtomicReference<RetentionConfig>                                      globalConfig  =
            new AtomicReference<>(RetentionConfig.DEFAULT);
    private final ConcurrentHashMap<String, RetentionConfig>                            tenantConfigs =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, RetentionConfig>> agentConfigs  =
            new ConcurrentHashMap<>();

    @Override
    public RetentionConfig current() {
        return globalConfig.get();
    }

    @Override
    public RetentionConfig current(String tenantId) {
        return tenantConfigs.getOrDefault(tenantId, globalConfig.get());
    }

    @Override
    public RetentionConfig current(String tenantId, String agentId) {
        var perAgent = agentConfigs.get(tenantId);
        if (perAgent != null) {
            var config = perAgent.get(agentId);
            if (config != null) {return config;}
        }
        return current(tenantId);
    }

    public void update(RetentionConfig newConfig) {
        globalConfig.set(newConfig);
    }

    public void update(String tenantId, RetentionConfig newConfig) {
        tenantConfigs.put(tenantId, newConfig);
    }

    public void update(String tenantId, String agentId, RetentionConfig newConfig) {
        agentConfigs.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                    .put(agentId, newConfig);
    }

    public void evictAbsent(Set<String> activeTenantIds) {
        tenantConfigs.keySet().retainAll(activeTenantIds);
        agentConfigs.keySet().retainAll(activeTenantIds);
    }
}

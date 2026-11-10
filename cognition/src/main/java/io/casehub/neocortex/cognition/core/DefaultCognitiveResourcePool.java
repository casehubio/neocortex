package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognitive.CognitiveResourcePoolConfig;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;

import java.util.concurrent.ConcurrentHashMap;

public class DefaultCognitiveResourcePool implements CognitiveResourcePool {

    private final CognitiveDefaultsRegistry defaultsRegistry;
    private final ConcurrentHashMap<String, Double> accumulators = new ConcurrentHashMap<>();

    public DefaultCognitiveResourcePool(CognitiveDefaultsRegistry defaultsRegistry) {
        this.defaultsRegistry = defaultsRegistry;
    }

    @Override
    public void recordEffort(String agentId, String tenantId, double amount) {
        if (amount <= 0) return;
        String key = agentId + ":" + tenantId;
        accumulators.merge(key, amount, Double::sum);
    }

    @Override
    public void recordRecovery(String agentId, String tenantId, double recoveryRate) {
        if (recoveryRate <= 0) return;
        String key = agentId + ":" + tenantId;
        accumulators.computeIfPresent(key, (k, v) -> {
            double recovered = v - recoveryRate;
            return recovered <= 0.0 ? null : recovered;
        });
    }

    @Override
    public double currentDepletion(String agentId, String tenantId) {
        String key = agentId + ":" + tenantId;
        double accumulator = accumulators.getOrDefault(key, 0.0);
        if (accumulator <= 0.0) return 0.0;
        var defaults = defaultsRegistry.forAgentOrDefaults(agentId);
        var poolConfig = defaults.cognitiveResourcePoolConfig();
        int maxEffort = poolConfig != null ? poolConfig.maxEffort()
                : CognitiveResourcePoolConfig.defaults().maxEffort();
        return Math.min(1.0, Math.log(1 + accumulator) / Math.log(1 + maxEffort));
    }
}

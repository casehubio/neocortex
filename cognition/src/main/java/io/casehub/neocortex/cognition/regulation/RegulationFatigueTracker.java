package io.casehub.neocortex.cognition.regulation;

import java.util.concurrent.ConcurrentHashMap;

public class RegulationFatigueTracker {

    // Bosse et al. 2010 Eq. 8 — fatigue accumulation rate per regulation tick
    static final double FATIGUE_RATE = 0.15;
    // Bosse et al. 2010 Eq. 9 — linear recovery per non-regulation tick
    static final double RECOVERY_RATE = 0.05;

    private final ConcurrentHashMap<String, Double> fatigue = new ConcurrentHashMap<>();

    public void recordRegulation(String agentId, String tenantId, double regulationEffort) {
        String key = agentId + ":" + tenantId;
        fatigue.merge(key, regulationEffort * FATIGUE_RATE, (old, inc) ->
                Math.min(1.0, old + inc));
    }

    public void recordRecovery(String agentId, String tenantId) {
        String key = agentId + ":" + tenantId;
        fatigue.computeIfPresent(key, (k, v) -> {
            double recovered = v - RECOVERY_RATE;
            return recovered <= 0.0 ? null : recovered;
        });
    }

    public double effectiveAlpha(String agentId, String tenantId, double baselineAlpha) {
        String key = agentId + ":" + tenantId;
        double currentFatigue = fatigue.getOrDefault(key, 0.0);
        return baselineAlpha * (1.0 - currentFatigue);
    }

    public double currentFatigue(String agentId, String tenantId) {
        return fatigue.getOrDefault(agentId + ":" + tenantId, 0.0);
    }

    public boolean isExhausted(String agentId, String tenantId) {
        return currentFatigue(agentId, tenantId) >= 1.0;
    }
}

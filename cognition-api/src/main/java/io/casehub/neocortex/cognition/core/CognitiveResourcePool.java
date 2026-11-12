package io.casehub.neocortex.cognition.core;

/**
 * Shared per-agent cognitive resource pool tracking total cognitive depletion
 * from all effortful subsystems. Models Hockey's (1997) compensatory control:
 * all effortful cognitive activity draws from limited processing capacity.
 * &lt;p&gt;
 * Sources call {@link #recordEffort} when active and {@link #recordRecovery}
 * when idle. Consumers call {@link #currentDepletion} and apply their own
 * domain-specific effects.
 */
public interface CognitiveResourcePool {

    void recordEffort(String agentId, String tenantId, double amount);

    void recordRecovery(String agentId, String tenantId, double recoveryRate);

    double currentDepletion(String agentId, String tenantId);
}

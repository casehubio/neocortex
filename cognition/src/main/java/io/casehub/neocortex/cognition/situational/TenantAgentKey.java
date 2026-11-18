package io.casehub.neocortex.cognition.situational;

public record TenantAgentKey(String tenantId, String agentId) {

    public TenantAgentKey {
        if (tenantId == null || tenantId.isEmpty()) throw new IllegalArgumentException("tenantId must not be empty");
        if (agentId == null || agentId.isEmpty()) throw new IllegalArgumentException("agentId must not be empty");
    }

    public int stripeIndex(int stripeCount) {
        int h = hashCode();
        h ^= (h >>> 16);
        return h & (stripeCount - 1);
    }
}

package io.casehub.neocortex.memory.experience;

/**
 * @param agentId the agent whose experience is being graduated; used to resolve
 *                capacity for Cattell's (1971) Investment Theory modulation.
 *                Nullable for backward compatibility.
 */
public record GraduationContext(int corroboratingCount, int textSimilarityCount,
                                 String tenantId, String agentId) {
    public GraduationContext(int corroboratingCount, int textSimilarityCount, String tenantId) {
        this(corroboratingCount, textSimilarityCount, tenantId, null);
    }

    public GraduationContext(int corroboratingCount, String tenantId) {
        this(corroboratingCount, 0, tenantId, null);
    }
}

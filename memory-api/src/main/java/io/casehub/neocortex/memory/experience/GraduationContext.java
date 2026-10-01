package io.casehub.neocortex.memory.experience;

public record GraduationContext(int corroboratingCount, int textSimilarityCount, String tenantId) {
    public GraduationContext(int corroboratingCount, String tenantId) {
        this(corroboratingCount, 0, tenantId);
    }
}

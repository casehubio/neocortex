package io.casehub.neocortex.memory.mood;

/**
 * CDI event fired after an affect trajectory entry is stored.
 * Downstream consumers (e.g., trajectory-aware curiosity) can observe this.
 */
public record AffectRecorded(String nodeId, String tenantId, String memoryId) {}

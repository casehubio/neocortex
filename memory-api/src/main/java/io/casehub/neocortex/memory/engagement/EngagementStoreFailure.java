package io.casehub.neocortex.memory.engagement;

public record EngagementStoreFailure(int inputIndex, EngagementEvent event, RuntimeException cause) {}

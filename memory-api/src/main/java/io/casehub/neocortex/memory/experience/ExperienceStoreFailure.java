package io.casehub.neocortex.memory.experience;

public record ExperienceStoreFailure(int inputIndex, ExperienceEvent event, RuntimeException cause) {}

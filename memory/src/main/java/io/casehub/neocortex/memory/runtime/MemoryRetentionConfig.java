package io.casehub.neocortex.memory.runtime;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.memory.retention")
public interface MemoryRetentionConfig {
    @WithDefault("false")
    boolean enabled();

    Optional<String> domain();

    Optional<Integer> maxAgeDays();

    Optional<Double> minImportance();
}

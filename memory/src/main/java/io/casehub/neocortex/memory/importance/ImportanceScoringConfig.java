package io.casehub.neocortex.memory.importance;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.memory.importance")
public interface ImportanceScoringConfig {
    @WithDefault("false")
    boolean enabled();

    @WithDefault("8")
    int batchSize();

    @WithDefault("5000")
    long batchDeadlineMs();
}

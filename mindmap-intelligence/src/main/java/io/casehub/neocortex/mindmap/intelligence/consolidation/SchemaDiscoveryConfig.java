package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.mindmap.schema-discovery")
public interface SchemaDiscoveryConfig {
    @WithDefault("0.8")
    double threshold();

    @WithDefault("5")
    int minSamples();

    @WithDefault("0.95")
    double requiredThreshold();
}

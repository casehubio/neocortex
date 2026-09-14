package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.consolidation.graduation")
public interface ExperienceConsolidationConfig {
    @WithDefault("0.5") double threshold();
    @WithDefault("20") int maxPerPass();
}

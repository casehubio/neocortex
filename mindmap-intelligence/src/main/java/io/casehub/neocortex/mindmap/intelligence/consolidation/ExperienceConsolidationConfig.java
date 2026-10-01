package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.mindmap.consolidation.graduation")
public interface ExperienceConsolidationConfig {
    @WithDefault("0.5")
    double threshold();

    @WithDefault("20")
    int maxPerPass();

    @WithDefault("3")
    int minCorroboration();

    TextSimilarity textSimilarity();

    interface TextSimilarity {
        @WithDefault("false")
        boolean enabled();

        @WithDefault("0.8")
        double embeddingThreshold();

        @WithDefault("0.5")
        double keywordThreshold();
    }
}

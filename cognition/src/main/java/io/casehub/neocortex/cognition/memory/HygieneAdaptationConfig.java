package io.casehub.neocortex.cognition.memory;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.cognition.hygiene-adaptation")
public interface HygieneAdaptationConfig {
    @WithDefault("false")
    boolean enabled();

    @WithDefault("0.3")
    double evictionPressureUpperThreshold();

    @WithDefault("0.05")
    double evictionPressureLowerThreshold();

    @WithDefault("0.02")
    double stepSize();

    @WithDefault("0.01")
    double minRetentionThreshold();

    @WithDefault("0.5")
    double maxRetentionThreshold();

    @WithDefault("3")
    int stabilityThreshold();

    @WithDefault("0.3")
    double cbrMinSimilarity();
}

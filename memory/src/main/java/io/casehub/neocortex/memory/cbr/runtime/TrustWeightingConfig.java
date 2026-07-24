package io.casehub.neocortex.memory.cbr.runtime;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.cbr.trust-weighting")
public interface TrustWeightingConfig {
    @WithDefault("false")
    boolean enabled();

    @WithDefault("0.3")
    double influence();

    @WithDefault("0.5")
    double trajectorySensitivity();
}

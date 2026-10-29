package io.casehub.neocortex.caps.cbr;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.caps.classification.cbr")
public interface ClassificationCbrConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("0.85")
    double minSimilarity();

    @WithDefault("haiku")
    String modelVersion();
}

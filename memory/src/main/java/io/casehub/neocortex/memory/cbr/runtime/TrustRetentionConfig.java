package io.casehub.neocortex.memory.cbr.runtime;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

@ConfigMapping(prefix = "casehub.cbr.trust-retention")
public interface TrustRetentionConfig {
    @WithDefault("false")
    boolean enabled();

    @WithDefault("24h")
    String interval();

    @WithDefault("0.3")
    double minCurrentTrust();

    String domain();

    List<String> caseTypes();
}

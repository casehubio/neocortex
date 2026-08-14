package io.casehub.neocortex.memory.cbr.runtime;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "casehub.cbr.retention")
public interface CbrRetentionConfig {
    @WithDefault("false")
    boolean enabled();

    @WithDefault("24h")
    String interval();

    @WithDefault("")
    String domain();

    @WithDefault("")
    List<String> caseTypes();

    Optional<Integer> maxAgeDays();

    Optional<Integer> maxCasesPerType();

    Optional<Double> minTrustScore();
}

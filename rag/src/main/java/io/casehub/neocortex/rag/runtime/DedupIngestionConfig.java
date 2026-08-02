package io.casehub.neocortex.rag.runtime;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.rag.ingestion.dedup")
public interface DedupIngestionConfig {
    @WithDefault("true")
    boolean enabled();

    @WithDefault("0.95")
    double threshold();

    Optional<String> logPath();
}

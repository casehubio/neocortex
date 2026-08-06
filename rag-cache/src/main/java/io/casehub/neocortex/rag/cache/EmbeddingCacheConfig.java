package io.casehub.neocortex.rag.cache;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.rag.embedding-cache")
public interface EmbeddingCacheConfig {
    @WithDefault("false")
    boolean enabled();

    Optional<String> path();

    Optional<String> versionSuffix();
}

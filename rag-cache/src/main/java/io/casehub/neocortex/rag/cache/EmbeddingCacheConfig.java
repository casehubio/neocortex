package io.casehub.neocortex.rag.cache;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.rag.embedding-cache")
public interface EmbeddingCacheConfig {
    @WithDefault("false")
    boolean enabled();

    @WithDefault("")
    String path();

    @WithDefault("")
    String versionSuffix();
}

package io.casehub.rag.hyde;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.rag.hyde")
public interface HydeConfig {

    @WithDefault("false")
    boolean enabled();

    @WithDefault("llm")
    String mode();

    Optional<String> promptTemplate();

    Optional<String> template();
}

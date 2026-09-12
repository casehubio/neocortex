package io.casehub.neocortex.rag.runtime;

import java.util.Optional;

public interface DedupIngestionConfig {
    boolean enabled();
    double threshold();
    Optional<String> logPath();
}

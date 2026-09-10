package io.casehub.neocortex.rag;

import java.util.Map;

public record ScoringContext(Map<String, String> versionProfile) {
    public static final ScoringContext EMPTY = new ScoringContext(Map.of());

    public ScoringContext {
        versionProfile = Map.copyOf(versionProfile);
    }
}

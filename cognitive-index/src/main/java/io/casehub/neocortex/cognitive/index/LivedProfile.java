package io.casehub.neocortex.cognitive.index;

import java.util.List;
import java.util.Objects;

public record LivedProfile(List<WeightedTerm> profile, String authoredFingerprint) {
    public LivedProfile {
        Objects.requireNonNull(authoredFingerprint, "authoredFingerprint required");
        profile = profile != null ? List.copyOf(profile) : List.of();
    }
}

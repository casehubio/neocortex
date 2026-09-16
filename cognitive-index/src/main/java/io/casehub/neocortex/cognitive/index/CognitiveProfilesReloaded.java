package io.casehub.neocortex.cognitive.index;

import java.util.Collection;
import java.util.List;

public record CognitiveProfilesReloaded(Collection<CognitiveDefaults> profiles) {
    public CognitiveProfilesReloaded {
        profiles = List.copyOf(profiles);
    }
}

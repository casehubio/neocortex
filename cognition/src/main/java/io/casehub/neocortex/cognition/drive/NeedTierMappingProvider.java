package io.casehub.neocortex.cognition.drive;

import io.casehub.neocortex.cognition.need.NeedTier;

import java.util.Map;
import java.util.Set;

public interface NeedTierMappingProvider {
    Map<String, Set<NeedTier>> tierMapping();

    static NeedTierMappingProvider empty() {
        return Map::of;
    }
}

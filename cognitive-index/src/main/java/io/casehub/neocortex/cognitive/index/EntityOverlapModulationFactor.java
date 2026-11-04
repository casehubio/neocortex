package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.ModulationFactor;
import io.casehub.neocortex.entity.token.TokenFormat;
import io.casehub.neocortex.cognitive.ModulationProfile;
import io.casehub.neocortex.memory.Memory;

import java.util.Set;

public class EntityOverlapModulationFactor implements ModulationFactor<Memory> {

    private static final double BOOST_PER_OVERLAP = 0.15;

    private final Set<String> contextEntityNodeIds;

    public EntityOverlapModulationFactor(Set<String> contextEntityNodeIds) {
        this.contextEntityNodeIds = Set.copyOf(contextEntityNodeIds);
    }

    @Override
    public double apply(Memory item, ModulationProfile<Memory> profile) {
        if (contextEntityNodeIds.isEmpty()) {return 1.0;}

        Set<String> memoryEntities = TokenFormat.extractEntityIds(item.text());
        if (memoryEntities.isEmpty()) {return 1.0;}

        long overlap = memoryEntities.stream()
                                     .filter(contextEntityNodeIds::contains)
                                     .count();

        return 1.0 + overlap * BOOST_PER_OVERLAP;
    }
}

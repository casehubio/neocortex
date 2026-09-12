package io.casehub.neocortex.cognitive.index;

public record DomainSignal(
        String subgraphId,
        AffectTrajectory trajectory,
        int entityCount,
        int memoryCount,
        int bucketCount
) {}
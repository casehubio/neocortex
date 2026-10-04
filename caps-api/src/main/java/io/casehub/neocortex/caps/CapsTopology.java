package io.casehub.neocortex.caps;

import java.util.List;
import java.util.Map;

public record CapsTopology(
    int version,
    Map<String, CapsNode> nodes,
    List<CapsConnection> connections,
    Map<String, DispositionModifier> dispositionModifiers,
    List<DistortionDefinition> distortions,
    WeightUpdateParameters weightUpdate
) {}

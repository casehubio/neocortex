package io.casehub.neocortex.caps;

import java.util.Map;

public record DispositionModifier(
    Map<String, Map<String, Double>> valueModifiers
) {}

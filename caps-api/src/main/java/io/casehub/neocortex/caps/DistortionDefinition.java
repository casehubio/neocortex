package io.casehub.neocortex.caps;

import java.util.List;

public record DistortionDefinition(
    String id,
    double baseThreshold,
    double multiplierMin,
    double multiplierMax,
    DistortionEffect effect,
    double negativeWeight,
    double positiveWeight,
    List<String> targetCategories
) {}

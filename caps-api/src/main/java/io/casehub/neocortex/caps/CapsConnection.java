package io.casehub.neocortex.caps;

import java.util.List;

public record CapsConnection(
    String id,
    String from,
    String to,
    double defaultWeight,
    WeightProvenance provenance,
    String source,
    List<String> tags
) {}

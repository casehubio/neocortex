package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;

public record ParsedRelationship(
    String source,
    String target,
    String type,
    ConfidenceOrigin origin
) {}

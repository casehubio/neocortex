package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;

public record ExtractedRelationship(
    String edgeId,
    String sourceName,
    String targetName,
    String edgeType,
    ConfidenceOrigin confidenceOrigin
) {}

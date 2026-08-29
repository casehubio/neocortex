package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;

record ParsedRelationship(
    String source,
    String target,
    String type,
    ConfidenceOrigin origin
) {}

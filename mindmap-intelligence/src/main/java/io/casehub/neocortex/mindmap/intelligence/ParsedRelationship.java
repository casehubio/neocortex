package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.ConfidenceOrigin;

record ParsedRelationship(
    String source,
    String target,
    String type,
    ConfidenceOrigin confidence
) {}

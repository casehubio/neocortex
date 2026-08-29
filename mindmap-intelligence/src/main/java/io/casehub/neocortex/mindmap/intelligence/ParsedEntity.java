package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;

import java.util.Map;

record ParsedEntity(
    String name,
    String type,
    Map<String, String> properties,
    ConfidenceOrigin origin
) {}

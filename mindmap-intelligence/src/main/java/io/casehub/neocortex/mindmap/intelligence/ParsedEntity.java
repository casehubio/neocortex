package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.ConfidenceOrigin;

import java.util.Map;

record ParsedEntity(
    String name,
    String type,
    Map<String, String> properties,
    ConfidenceOrigin confidence
) {}

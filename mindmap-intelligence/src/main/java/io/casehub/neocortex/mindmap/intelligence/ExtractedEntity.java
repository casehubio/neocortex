package io.casehub.neocortex.mindmap.intelligence;

import java.util.Map;

public record ExtractedEntity(
    String nodeId,
    String name,
    boolean created,
    String subgraphType,
    Map<String, String> properties
) {}

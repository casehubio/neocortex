package io.casehub.neocortex.cognitive.observability;

import java.util.List;

public record EntityTrace(
    String entityId,
    String entityName,
    List<TraceEvent> events
) {}

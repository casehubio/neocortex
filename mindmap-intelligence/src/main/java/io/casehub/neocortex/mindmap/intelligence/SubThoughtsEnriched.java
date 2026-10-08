package io.casehub.neocortex.mindmap.intelligence;

import java.util.List;

public record SubThoughtsEnriched(
    String memoryId,
    String agentId,
    String tenantId,
    List<SubThoughtExtractor.ParsedSubThought> subThoughts
) {}

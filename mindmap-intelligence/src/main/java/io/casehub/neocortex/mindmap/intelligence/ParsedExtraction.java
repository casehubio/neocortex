package io.casehub.neocortex.mindmap.intelligence;

import java.util.List;

public record ParsedExtraction(
    List<ParsedEntity> entities,
    List<ParsedRelationship> relationships,
    List<ParsedContradiction> contradictions
) {}

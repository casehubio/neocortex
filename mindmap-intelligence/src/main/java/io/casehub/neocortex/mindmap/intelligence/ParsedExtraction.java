package io.casehub.neocortex.mindmap.intelligence;

import java.util.List;

record ParsedExtraction(
    List<ParsedEntity> entities,
    List<ParsedRelationship> relationships,
    List<ParsedContradiction> contradictions
) {}

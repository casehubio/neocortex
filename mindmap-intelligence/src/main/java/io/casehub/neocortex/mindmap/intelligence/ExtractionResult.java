package io.casehub.neocortex.mindmap.intelligence;

import java.util.List;

public record ExtractionResult(
    List<ExtractedEntity> entities,
    List<ExtractedRelationship> relationships,
    List<Contradiction> contradictions,
    List<String> entityNames
) {
    public static final ExtractionResult EMPTY =
        new ExtractionResult(List.of(), List.of(), List.of(), List.of());
}

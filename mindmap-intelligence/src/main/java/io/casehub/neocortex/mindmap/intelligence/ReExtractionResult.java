package io.casehub.neocortex.mindmap.intelligence;

import java.util.List;

public record ReExtractionResult(
    int sourcesProcessed,
    int annotationsCreated,
    List<String> errors
) {
    public static final ReExtractionResult EMPTY = new ReExtractionResult(0, 0, List.of());
}

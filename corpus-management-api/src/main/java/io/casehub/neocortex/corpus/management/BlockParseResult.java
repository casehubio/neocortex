package io.casehub.neocortex.corpus.management;

import java.util.List;

public record BlockParseResult(
    String cleanText,
    List<BlockAnnotation> annotations
) {
    public BlockParseResult {
        if (cleanText == null) cleanText = "";
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
    }
}

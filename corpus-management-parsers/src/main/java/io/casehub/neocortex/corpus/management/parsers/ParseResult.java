package io.casehub.neocortex.corpus.management.parsers;

import java.util.List;
import java.util.Map;

public record ParseResult(
    String positionalMarkdown,
    Layout layout,
    Map<String, String> extractedMetadata,
    List<ImageRegion> imageRegions
) {
    public ParseResult {
        if (positionalMarkdown == null) throw new IllegalArgumentException("positionalMarkdown must not be null");
        if (layout == null) throw new IllegalArgumentException("layout must not be null");
        extractedMetadata = extractedMetadata == null ? Map.of() : Map.copyOf(extractedMetadata);
        imageRegions = imageRegions == null ? List.of() : List.copyOf(imageRegions);
    }
}

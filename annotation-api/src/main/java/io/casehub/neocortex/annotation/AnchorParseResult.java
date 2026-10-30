package io.casehub.neocortex.annotation;

import java.util.List;

public record AnchorParseResult(String cleanText, List<ParsedAnchor> anchors) {}

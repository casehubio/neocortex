package io.casehub.neocortex.caps;

import java.util.List;

public record CapsNode(
    String id,
    NodeType type,
    NodeRange range,
    String category,
    List<String> sourceModels,
    List<String> keywords
) {}

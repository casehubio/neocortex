package io.casehub.neocortex.caps;

import java.util.List;
import java.util.Map;

public record CapsNode(
        String id,
        NodeType type,
        NodeRange range,
        String category,
        List<String> sourceModels,
        Map<String, Double> vocabulary
) {}

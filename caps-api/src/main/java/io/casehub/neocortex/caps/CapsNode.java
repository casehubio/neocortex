package io.casehub.neocortex.caps;

import java.util.List;
import java.util.Map;

public record CapsNode(
        String id,
        NodeType type,
        NodeRange range,
        String category,
        List<String> sourceModels,
        Map<String, Double> vocabulary,
        String description
) {
    public CapsNode(String id, NodeType type, NodeRange range, String category,
                    List<String> sourceModels, Map<String, Double> vocabulary) {
        this(id, type, range, category, sourceModels, vocabulary, "");
    }
}

package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record NodeInput(
        String name,
        String subgraphId,
        Confidence confidence,
        String provenance,
        Set<String> traits,
        Set<NodeRef> refs,
        Instant validFrom,
        Instant validUntil,
        Double pleasure,
        Double arousal,
        Double dominance,
        Map<String, String> properties
                       ) {

    public NodeInput {
        if (name == null || name.isBlank()) {throw new IllegalArgumentException("name must not be blank");}
        if (subgraphId == null || subgraphId.isBlank()) {
            throw new IllegalArgumentException("subgraphId must not be blank");
        }
        traits     = traits == null ? Set.of() : Set.copyOf(traits);
        refs       = refs == null ? Set.of() : Set.copyOf(refs);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}

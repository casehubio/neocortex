package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeRef;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

record StubNode(
    String id, String name, String subgraphId,
    Confidence confidence, String provenance,
    Instant createdAt, Instant updatedAt,
    Instant validFrom, Instant validUntil,
    Set<String> traits, Set<NodeRef> refs,
    Double pleasure, Double arousal, Double dominance,
    Map<String, String> properties
) implements MindMapNode {

    static StubNode named(String name) {
        return new StubNode(
            "id-" + name.toLowerCase(), name, "sg-1",
            new Confidence(ConfidenceOrigin.STATED, 0.9, Instant.now()),
            null, Instant.now(), Instant.now(), null, null,
            Set.of(), Set.of(), null, null, null, Map.of());
    }

    static StubNode withRefs(String name, Set<NodeRef> refs) {
        return new StubNode(
            "id-" + name.toLowerCase(), name, "sg-1",
            new Confidence(ConfidenceOrigin.STATED, 0.9, Instant.now()),
            null, Instant.now(), Instant.now(), null, null,
            Set.of(), refs, null, null, null, Map.of());
    }

    @Override
    public Optional<String> property(String key) {
        return Optional.ofNullable(properties.get(key));
    }
}

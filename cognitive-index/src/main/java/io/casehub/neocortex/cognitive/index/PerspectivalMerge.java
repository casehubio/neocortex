package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeRef;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PerspectivalMerge {

    private PerspectivalMerge() {}

    public static MindMapNode merge(MindMapNode shared, MindMapNode overlay) {
        Double pleasure = overlay.pleasure() != null ? overlay.pleasure() : shared.pleasure();
        Double arousal = overlay.arousal() != null ? overlay.arousal() : shared.arousal();
        Double dominance = overlay.dominance() != null ? overlay.dominance() : shared.dominance();
        Confidence confidence = overlay.confidence() != null ? overlay.confidence() : shared.confidence();

        Map<String, String> mergedProps = new HashMap<>(shared.properties());
        mergedProps.putAll(overlay.properties());

        return new MergedNode(
            shared.id(), shared.name(), shared.subgraphId(),
            confidence, shared.provenance(),
            shared.createdAt(), shared.updatedAt(),
            shared.validFrom(), shared.validUntil(),
            shared.traits(), shared.refs(),
            pleasure, arousal, dominance,
            Map.copyOf(mergedProps)
        );
    }

    private record MergedNode(
        String id, String name, String subgraphId,
        Confidence confidence, String provenance,
        Instant createdAt, Instant updatedAt,
        Instant validFrom, Instant validUntil,
        Set<String> traits, Set<NodeRef> refs,
        Double pleasure, Double arousal, Double dominance,
        Map<String, String> props
    ) implements MindMapNode {
        @Override
        public Optional<String> property(String key) {
            return Optional.ofNullable(props.get(key));
        }
        @Override
        public Map<String, String> properties() {
            return props;
        }
    }
}

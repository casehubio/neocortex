package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.neocortex.mindmap.OverlayRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PerspectivalMergeTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final Confidence SHARED_CONF =
        new Confidence(ConfidenceOrigin.STATED, 0.9, NOW);
    private static final Confidence OVERLAY_CONF =
        new Confidence(ConfidenceOrigin.INFERRED, 0.7, NOW);

    @Test
    void overlayPadReplacesSharedNullPad() {
        MindMapNode shared = sharedNode(null, null, null, SHARED_CONF, Map.of());
        MindMapNode overlay = overlayNode(0.8, 0.3, -0.2, null, Map.of());

        MindMapNode merged = PerspectivalMerge.merge(shared, overlay);

        assertThat(merged.pleasure()).isEqualTo(0.8);
        assertThat(merged.arousal()).isEqualTo(0.3);
        assertThat(merged.dominance()).isEqualTo(-0.2);
    }

    @Test
    void overlayConfidenceReplacesSharedConfidence() {
        MindMapNode shared = sharedNode(null, null, null, SHARED_CONF, Map.of());
        MindMapNode overlay = overlayNode(null, null, null, OVERLAY_CONF, Map.of());

        MindMapNode merged = PerspectivalMerge.merge(shared, overlay);

        assertThat(merged.confidence().value()).isEqualTo(0.7);
        assertThat(merged.confidence().origin()).isEqualTo(ConfidenceOrigin.INFERRED);
    }

    @Test
    void overlayPropertiesMergeOverlayWins() {
        MindMapNode shared = sharedNode(null, null, null, SHARED_CONF,
            Map.of("birthday", "1945-03-12", "surname", "Smith"));
        MindMapNode overlay = overlayNode(null, null, null, null,
            Map.of("notes", "love her", "surname", "Smithy"));

        MindMapNode merged = PerspectivalMerge.merge(shared, overlay);

        assertThat(merged.properties()).containsEntry("birthday", "1945-03-12");
        assertThat(merged.properties()).containsEntry("notes", "love her");
        assertThat(merged.properties()).containsEntry("surname", "Smithy");
    }

    @Test
    void nullOverlayPadKeepsSharedPad() {
        MindMapNode shared = sharedNode(0.5, 0.3, 0.1, SHARED_CONF, Map.of());
        MindMapNode overlay = overlayNode(null, null, null, null, Map.of());

        MindMapNode merged = PerspectivalMerge.merge(shared, overlay);

        assertThat(merged.pleasure()).isEqualTo(0.5);
        assertThat(merged.arousal()).isEqualTo(0.3);
        assertThat(merged.dominance()).isEqualTo(0.1);
    }

    @Test
    void sharedNameTraitsSubgraphPreserved() {
        MindMapNode shared = new StubNode(
            "shared-1", "Grandma", "sg-family",
            SHARED_CONF, null, NOW, NOW, null, null,
            Set.of("Personable"), Set.of(), null, null, null,
            Map.of("birthday", "1945-03-12"));
        MindMapNode overlay = overlayNode(0.9, 0.3, 0.5, null, Map.of("notes", "love her"));

        MindMapNode merged = PerspectivalMerge.merge(shared, overlay);

        assertThat(merged.id()).isEqualTo("shared-1");
        assertThat(merged.name()).isEqualTo("Grandma");
        assertThat(merged.subgraphId()).isEqualTo("sg-family");
        assertThat(merged.traits()).containsExactly("Personable");
        assertThat(merged.createdAt()).isEqualTo(NOW);
    }

    private MindMapNode sharedNode(Double p, Double a, Double d,
            Confidence conf, Map<String, String> props) {
        return new StubNode("shared-1", "Grandma", "sg-family",
            conf, null, NOW, NOW, null, null,
            Set.of("Personable"), Set.of(), p, a, d, props);
    }

    private MindMapNode overlayNode(Double p, Double a, Double d,
            Confidence conf, Map<String, String> props) {
        return new StubNode("overlay-1", "Grandma", "sg-overlays",
            conf, null, NOW, NOW, null, null,
            Set.of("overlay"), Set.of(OverlayRef.of("shared-1")),
            p, a, d, props);
    }
}

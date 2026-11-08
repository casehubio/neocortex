package io.casehub.neocortex.corpus.management;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PanelDefinitionTest {

    @Test
    void validPanel() {
        var panel = new PanelDefinition("main", 0.0, 0.0, 1.0, 1.0);
        assertThat(panel.id()).isEqualTo("main");
        assertThat(panel.x()).isEqualTo(0.0);
        assertThat(panel.y()).isEqualTo(0.0);
        assertThat(panel.w()).isEqualTo(1.0);
        assertThat(panel.h()).isEqualTo(1.0);
    }

    @Test
    void rejectsBlankId() {
        assertThatThrownBy(() -> new PanelDefinition("", 0.0, 0.0, 1.0, 1.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new PanelDefinition(null, 0.0, 0.0, 1.0, 1.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeX() {
        assertThatThrownBy(() -> new PanelDefinition("p", -0.1, 0.0, 1.0, 1.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsXAboveOne() {
        assertThatThrownBy(() -> new PanelDefinition("p", 1.1, 0.0, 1.0, 1.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroWidth() {
        assertThatThrownBy(() -> new PanelDefinition("p", 0.0, 0.0, 0.0, 1.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroHeight() {
        assertThatThrownBy(() -> new PanelDefinition("p", 0.0, 0.0, 1.0, 0.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structuredChunkCreation() {
        var chunk = new StructuredChunk("some text", Map.of("page", "1", "panel", "left"));
        assertThat(chunk.text()).isEqualTo("some text");
        assertThat(chunk.metadata()).containsEntry("page", "1");
        assertThat(chunk.metadata()).containsEntry("panel", "left");
    }

    @Test
    void structuredChunkRejectsNullText() {
        assertThatThrownBy(() -> new StructuredChunk(null, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structuredChunkDefaultsNullMetadata() {
        var chunk = new StructuredChunk("text", null);
        assertThat(chunk.metadata()).isEmpty();
    }

    @Test
    void layoutClassificationCreation() {
        var panels = List.of(new PanelDefinition("left", 0.0, 0.0, 0.5, 1.0));
        var classification = new LayoutClassification("2-column", panels);
        assertThat(classification.layoutName()).isEqualTo("2-column");
        assertThat(classification.panels()).hasSize(1);
    }

    @Test
    void layoutClassificationRejectsBlankName() {
        assertThatThrownBy(() -> new LayoutClassification("", List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

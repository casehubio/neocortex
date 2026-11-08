package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.PanelDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LayoutPresetsTest {

    @Test
    void singlePresetHasOnePanel() {
        List<PanelDefinition> panels = LayoutPresets.resolve("single");
        assertThat(panels).hasSize(1);
        assertThat(panels.get(0).id()).isEqualTo("main");
        assertThat(panels.get(0).w()).isEqualTo(1.0);
        assertThat(panels.get(0).h()).isEqualTo(1.0);
    }

    @Test
    void twoColumnPresetHasTwoPanels() {
        List<PanelDefinition> panels = LayoutPresets.resolve("2-column");
        assertThat(panels).hasSize(2);
        assertThat(panels.get(0).id()).isEqualTo("left");
        assertThat(panels.get(1).id()).isEqualTo("right");
    }

    @Test
    void threeColumnPresetHasThreePanels() {
        List<PanelDefinition> panels = LayoutPresets.resolve("3-column");
        assertThat(panels).hasSize(3);
        assertThat(panels.get(0).id()).isEqualTo("left");
        assertThat(panels.get(1).id()).isEqualTo("center");
        assertThat(panels.get(2).id()).isEqualTo("right");
    }

    @Test
    void headerBodyFooterPresetHasThreePanels() {
        List<PanelDefinition> panels = LayoutPresets.resolve("header-body-footer");
        assertThat(panels).hasSize(3);
        assertThat(panels.stream().map(PanelDefinition::id).toList())
            .containsExactly("header", "body", "footer");
    }

    @Test
    void grid2x2PresetHasFourPanels() {
        List<PanelDefinition> panels = LayoutPresets.resolve("grid-2x2");
        assertThat(panels).hasSize(4);
        assertThat(panels.stream().map(PanelDefinition::id).toList())
            .containsExactly("top-left", "top-right", "bottom-left", "bottom-right");
    }

    @Test
    void allPresetsHaveValidCoordinates() {
        for (var entry : LayoutPresets.PRESETS.entrySet()) {
            for (PanelDefinition panel : entry.getValue()) {
                assertThat(panel.x()).as("preset=%s panel=%s x", entry.getKey(), panel.id())
                    .isBetween(0.0, 1.0);
                assertThat(panel.y()).as("preset=%s panel=%s y", entry.getKey(), panel.id())
                    .isBetween(0.0, 1.0);
                assertThat(panel.w()).as("preset=%s panel=%s w", entry.getKey(), panel.id())
                    .isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
                assertThat(panel.h()).as("preset=%s panel=%s h", entry.getKey(), panel.id())
                    .isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
            }
        }
    }

    @Test
    void unknownPresetThrows() {
        assertThatThrownBy(() -> LayoutPresets.resolve("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown layout preset");
    }

    @Test
    void presetMapContainsAllExpectedKeys() {
        assertThat(LayoutPresets.PRESETS.keySet())
            .containsExactlyInAnyOrder("single", "2-column", "3-column",
                "header-body-footer", "grid-2x2");
    }
}

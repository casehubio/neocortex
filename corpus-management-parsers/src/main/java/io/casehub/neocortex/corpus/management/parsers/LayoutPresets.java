package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.PanelDefinition;

import java.util.List;
import java.util.Map;

public final class LayoutPresets {

    public static final Map<String, List<PanelDefinition>> PRESETS = Map.ofEntries(
        Map.entry("single", List.of(
            new PanelDefinition("main", 0.0, 0.0, 1.0, 1.0))),
        Map.entry("2-column", List.of(
            new PanelDefinition("left", 0.0, 0.0, 0.48, 1.0),
            new PanelDefinition("right", 0.52, 0.0, 0.48, 1.0))),
        Map.entry("3-column", List.of(
            new PanelDefinition("left", 0.0, 0.0, 0.31, 1.0),
            new PanelDefinition("center", 0.345, 0.0, 0.31, 1.0),
            new PanelDefinition("right", 0.69, 0.0, 0.31, 1.0))),
        Map.entry("header-body-footer", List.of(
            new PanelDefinition("header", 0.0, 0.9, 1.0, 0.1),
            new PanelDefinition("body", 0.0, 0.1, 1.0, 0.78),
            new PanelDefinition("footer", 0.0, 0.0, 1.0, 0.08))),
        Map.entry("grid-2x2", List.of(
            new PanelDefinition("top-left", 0.0, 0.52, 0.48, 0.48),
            new PanelDefinition("top-right", 0.52, 0.52, 0.48, 0.48),
            new PanelDefinition("bottom-left", 0.0, 0.0, 0.48, 0.48),
            new PanelDefinition("bottom-right", 0.52, 0.0, 0.48, 0.48)))
    );

    public static List<PanelDefinition> resolve(String name) {
        List<PanelDefinition> panels = PRESETS.get(name);
        if (panels == null) throw new IllegalArgumentException("Unknown layout preset: " + name);
        return panels;
    }

    private LayoutPresets() {}
}

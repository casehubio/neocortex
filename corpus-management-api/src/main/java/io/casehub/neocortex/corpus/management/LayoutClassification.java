package io.casehub.neocortex.corpus.management;

import java.util.List;

public record LayoutClassification(String layoutName, List<PanelDefinition> panels) {
    public LayoutClassification {
        if (layoutName == null || layoutName.isBlank()) throw new IllegalArgumentException("layoutName must not be blank");
        panels = panels == null ? List.of() : List.copyOf(panels);
    }
}

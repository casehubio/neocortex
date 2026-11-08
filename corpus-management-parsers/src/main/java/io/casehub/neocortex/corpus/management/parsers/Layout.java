package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.PanelDefinition;

import java.util.List;

public record Layout(
    String name,
    List<PanelDefinition> panels,
    int pageCount,
    int[] pageSize
) {
    public Layout {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        panels = panels == null ? List.of() : List.copyOf(panels);
        if (pageCount < 1) throw new IllegalArgumentException("pageCount must be >= 1");
        if (pageSize == null) pageSize = new int[]{612, 792};
        if (pageSize.length != 2) throw new IllegalArgumentException("pageSize must have exactly 2 elements [width, height]");
    }
}

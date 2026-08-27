package io.casehub.neocortex.mindmap;

import java.util.List;

public interface TraitRule {
    String traitName();
    boolean matches(MindMapNode node, List<MindMapEdge> edges);
}

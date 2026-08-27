package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.TraitRule;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ProjectlikeTraitRule implements TraitRule {

    @Override
    public String traitName() { return "Projectlike"; }

    @Override
    public boolean matches(MindMapNode node, List<MindMapEdge> edges) {
        boolean hasProperties = node.property("status").isPresent()
            || node.property("startDate").isPresent()
            || node.property("endDate").isPresent();
        boolean hasEdges = edges.stream()
            .anyMatch(e -> "contributes-to".equals(e.edgeType())
                || "depends-on".equals(e.edgeType()));
        return hasProperties || hasEdges;
    }
}

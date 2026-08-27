package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.TraitRule;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class OrganisationalTraitRule implements TraitRule {

    @Override
    public String traitName() { return "Organisational"; }

    @Override
    public boolean matches(MindMapNode node, List<MindMapEdge> edges) {
        boolean hasProperties = node.property("industry").isPresent()
            || node.property("size").isPresent()
            || node.property("location").isPresent();
        boolean hasEdges = edges.stream()
            .anyMatch(e -> "employs".equals(e.edgeType())
                || "subsidiary-of".equals(e.edgeType()));
        return hasProperties || hasEdges;
    }
}

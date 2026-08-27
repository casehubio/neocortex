package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.TraitRule;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PersonableTraitRule implements TraitRule {

    @Override
    public String traitName() { return "Personable"; }

    @Override
    public boolean matches(MindMapNode node, List<MindMapEdge> edges) {
        boolean hasProperties = node.property("birthday").isPresent()
            || node.property("role").isPresent()
            || node.property("email").isPresent()
            || node.property("phone").isPresent();
        boolean hasEdges = edges.stream()
            .anyMatch(e -> "parent-of".equals(e.edgeType())
                || "child-of".equals(e.edgeType())
                || "works-at".equals(e.edgeType()));
        return hasProperties || hasEdges;
    }
}

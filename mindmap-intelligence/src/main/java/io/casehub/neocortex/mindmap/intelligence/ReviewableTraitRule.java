package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.TraitRule;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ReviewableTraitRule implements TraitRule {

    @Override
    public String traitName() { return "Reviewable"; }

    @Override
    public boolean matches(MindMapNode node, List<MindMapEdge> edges) {
        return node.property("rating").isPresent()
            || node.property("reviewCount").isPresent()
            || node.property("priceRange").isPresent();
    }
}

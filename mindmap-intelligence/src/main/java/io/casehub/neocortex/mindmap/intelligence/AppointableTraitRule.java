package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.TraitRule;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class AppointableTraitRule implements TraitRule {

    @Override
    public String traitName() { return "Appointable"; }

    @Override
    public boolean matches(MindMapNode node, List<MindMapEdge> edges) {
        return node.property("eventKind").map("scheduled"::equals).orElse(false);
    }
}

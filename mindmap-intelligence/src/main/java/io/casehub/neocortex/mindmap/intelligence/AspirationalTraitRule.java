package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.TraitRule;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class AspirationalTraitRule implements TraitRule {

    @Override
    public String traitName() { return "Aspirational"; }

    @Override
    public boolean matches(MindMapNode node, List<MindMapEdge> edges) {
        return node.property("eventKind").map("anticipated"::equals).orElse(false)
            && node.property("eventValence").map("aspirational"::equals).orElse(false);
    }
}

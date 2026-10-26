package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.intelligence.consolidation.JaroWinkler;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FuzzyNodeResolver {

    public Optional<MindMapNode> resolve(MindMapStore store, String name,
            double threshold, String subgraphId, String tenantId) {
        if (name == null || name.isBlank()) return Optional.empty();

        List<MindMapNode> candidates;
        if (subgraphId != null) {
            candidates = store.nodesIn(subgraphId, tenantId);
        } else {
            candidates = new ArrayList<>();
            for (MindMapSubgraph sg : store.listSubgraphs(tenantId)) {
                candidates.addAll(store.nodesIn(sg.id(), tenantId));
            }
        }

        MindMapNode best = null;
        double bestScore = threshold;

        for (MindMapNode node : candidates) {
            double score = JaroWinkler.similarity(name, node.name());
            if (score > bestScore) {
                bestScore = score;
                best = node;
            }
        }

        return Optional.ofNullable(best);
    }
}

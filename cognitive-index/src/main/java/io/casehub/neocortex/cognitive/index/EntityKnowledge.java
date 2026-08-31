package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeRef;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record EntityKnowledge(
    MindMapNode node,
    List<MindMapEdge> edges,
    Map<MemoryDomain, List<Memory>> memories,
    AffectTrajectory trajectory,
    Set<NodeRef> unresolvedRefs,
    String tenantId
) {

    public EntityKnowledge {
        Objects.requireNonNull(node, "node required");
        Objects.requireNonNull(tenantId, "tenantId required");
        edges = edges == null ? List.of() : List.copyOf(edges);
        memories = memories == null ? Map.of() : Map.copyOf(memories);
        unresolvedRefs = unresolvedRefs == null ? Set.of() : Set.copyOf(unresolvedRefs);
    }
}

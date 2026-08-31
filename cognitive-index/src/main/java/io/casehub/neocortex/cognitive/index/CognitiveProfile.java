package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.engagement.EngagementEvents;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.mood.AffectEvents;
import io.casehub.neocortex.memory.mood.MoodEvents;
import io.casehub.neocortex.memory.reflection.ReflectionEvents;
import io.casehub.neocortex.memory.relationship.RelationshipEvents;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class CognitiveProfile {

    static final Set<MemoryDomain> DEFAULT_DOMAINS = Set.of(
        ExperienceEvents.DOMAIN,
        RelationshipEvents.DOMAIN,
        ReflectionEvents.DOMAIN,
        MoodEvents.DOMAIN,
        EngagementEvents.DOMAIN,
        AffectEvents.DOMAIN
    );

    private final MindMapStore mindMapStore;
    private final CaseMemoryStore memoryStore;
    private final CbrCaseMemoryStore cbrStore;

    @Inject
    public CognitiveProfile(Instance<MindMapStore> mindMapStore,
                            Instance<CaseMemoryStore> memoryStore,
                            Instance<CbrCaseMemoryStore> cbrStore) {
        this.mindMapStore = mindMapStore != null && mindMapStore.isResolvable() ? mindMapStore.get() : null;
        this.memoryStore = memoryStore != null && memoryStore.isResolvable() ? memoryStore.get() : null;
        this.cbrStore = cbrStore != null && cbrStore.isResolvable() ? cbrStore.get() : null;
    }

    CognitiveProfile(MindMapStore mindMapStore, CaseMemoryStore memoryStore, CbrCaseMemoryStore cbrStore) {
        this.mindMapStore = mindMapStore;
        this.memoryStore = memoryStore;
        this.cbrStore = cbrStore;
    }

    public Optional<EntityKnowledge> resolve(CognitiveProfileQuery query) {
        if (mindMapStore == null) {
            return Optional.empty();
        }

        MindMapNode node = resolveNode(query);
        if (node == null) {
            return Optional.empty();
        }

        List<String> entityIds = collectEntityIds(node);
        Set<NodeRef> unresolvedRefs = collectUnresolvedRefs(node);

        List<MindMapEdge> edges = query.includeEdges()
            ? mindMapStore.neighbors(node.id(), query.tenantId())
            : List.of();

        Set<MemoryDomain> domains = query.domains().isEmpty()
            ? DEFAULT_DOMAINS : query.domains();

        Map<MemoryDomain, List<Memory>> memories = queryMemories(entityIds, domains, query);

        AffectTrajectory trajectory = computeTrajectory(entityIds, memories, query);

        return Optional.of(new EntityKnowledge(node, edges, memories, trajectory, unresolvedRefs, query.tenantId()));
    }

    private MindMapNode resolveNode(CognitiveProfileQuery query) {
        try {
            if (query.nodeId() != null) {
                return mindMapStore.getNode(query.nodeId(), query.tenantId());
            } else {
                return mindMapStore.resolveNode(query.entityName(), query.subgraphId(), query.tenantId());
            }
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<String> collectEntityIds(MindMapNode node) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(node.id());
        ids.add(node.name());
        for (NodeRef ref : node.refs()) {
            if ("memory".equals(ref.scheme())) {
                ids.add(ref.id());
            }
        }
        return List.copyOf(ids);
    }

    private Set<NodeRef> collectUnresolvedRefs(MindMapNode node) {
        Set<NodeRef> unresolved = new LinkedHashSet<>();
        for (NodeRef ref : node.refs()) {
            if (!"memory".equals(ref.scheme())) {
                unresolved.add(ref);
            }
        }
        return unresolved;
    }

    private Map<MemoryDomain, List<Memory>> queryMemories(
            List<String> entityIds, Set<MemoryDomain> domains,
            CognitiveProfileQuery query) {
        if (memoryStore == null) {
            return Map.of();
        }
        Map<MemoryDomain, List<Memory>> result = new LinkedHashMap<>();
        for (MemoryDomain domain : domains) {
            List<Memory> memories = memoryStore.query(
                MemoryQuery.forEntities(entityIds, domain, query.tenantId())
                    .withLimit(query.memoryLimit())
                    .withOrder(MemoryOrder.CHRONOLOGICAL));
            if (!memories.isEmpty()) {
                result.put(domain, memories);
            }
        }
        return result;
    }

    private AffectTrajectory computeTrajectory(
            List<String> entityIds,
            Map<MemoryDomain, List<Memory>> memories,
            CognitiveProfileQuery query) {
        if (memoryStore == null) {
            return null;
        }

        List<Memory> affectMemories = memories.get(AffectEvents.DOMAIN);
        if (affectMemories == null) {
            affectMemories = memoryStore.query(
                MemoryQuery.forEntities(entityIds, AffectEvents.DOMAIN, query.tenantId())
                    .withLimit(query.memoryLimit())
                    .withOrder(MemoryOrder.CHRONOLOGICAL));
        }

        if (affectMemories.isEmpty()) {
            return null;
        }
        return AffectTrajectoryAnalyzer.analyze(affectMemories);
    }
}

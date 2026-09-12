package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
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
import io.casehub.neocortex.mindmap.OverlayRef;
import io.casehub.platform.api.identity.PrincipalId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.HashMap;
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

    private final MindMapStore         mindMapStore;
    private final CaseMemoryStore      memoryStore;
    private final PerspectivalResolver perspectivalResolver;

    @Inject
    public CognitiveProfile(Instance<MindMapStore> mindMapStore,
                            Instance<CaseMemoryStore> memoryStore) {
        this.mindMapStore         = mindMapStore != null && mindMapStore.isResolvable() ? mindMapStore.get() : null;
        this.memoryStore          = memoryStore != null && memoryStore.isResolvable() ? memoryStore.get() : null;
        this.perspectivalResolver = this.mindMapStore != null
                                    ? new PerspectivalResolver(this.mindMapStore) : null;
    }

    CognitiveProfile(MindMapStore mindMapStore, CaseMemoryStore memoryStore) {
        this.mindMapStore         = mindMapStore;
        this.memoryStore          = memoryStore;
        this.perspectivalResolver = mindMapStore != null
                                    ? new PerspectivalResolver(mindMapStore) : null;
    }

    public Optional<EntityKnowledge> resolve(CognitiveProfileQuery query) {
        if (mindMapStore == null) {
            return Optional.empty();
        }

        MindMapNode node = resolveNode(query);
        if (node == null) {
            return Optional.empty();
        }

        PrincipalId asSeenBy = query.asSeenBy();
        if (asSeenBy != null && perspectivalResolver != null) {
            List<MindMapNode> resolved = perspectivalResolver.resolve(
                    List.of(node), asSeenBy, query.tenantId());
            node = resolved.getFirst();
        }

        List<String> entityIds      = collectEntityIds(node);
        Set<NodeRef> unresolvedRefs = collectUnresolvedRefs(node);

        List<MindMapEdge> edges = query.includeEdges()
                                  ? mindMapStore.neighbors(node.id(), query.tenantId())
                                  : List.of();

        Set<MemoryDomain> domains = query.domains().isEmpty()
                                    ? DEFAULT_DOMAINS : query.domains();

        Map<MemoryDomain, List<Memory>> memories = queryMemories(entityIds, domains, query);

        AffectTrajectory trajectory = computeTrajectory(entityIds, memories, query);

        return Optional.of(new EntityKnowledge(node, edges, memories, trajectory, unresolvedRefs, query.tenantId(), asSeenBy));
    }

    public Map<PrincipalId, EntityKnowledge> compare(
            CognitiveProfileQuery query, Set<PrincipalId> agents) {
        if (mindMapStore == null || agents.isEmpty()) {
            return Map.of();
        }

        MindMapNode sharedNode = resolveNode(query);
        if (sharedNode == null) {
            return Map.of();
        }

        List<MindMapNode> allOverlays = perspectivalResolver != null
                                        ? perspectivalResolver.loadAllOverlays(query.tenantId())
                                        : List.of();

        Map<String, MindMapNode> overlaysByAgent = new HashMap<>();
        for (MindMapNode overlay : allOverlays) {
            String agentId = overlay.properties().get(OverlayRef.AGENT_ID);
            if (agentId != null) {
                OverlayRef.sharedNodeId(overlay).ifPresent(sid -> {
                    if (sid.equals(sharedNode.id())) {
                        overlaysByAgent.put(agentId, overlay);
                    }
                });
            }
        }

        Set<MemoryDomain> domains = query.domains().isEmpty()
                                    ? DEFAULT_DOMAINS : query.domains();

        Map<PrincipalId, EntityKnowledge> result = new LinkedHashMap<>();
        for (PrincipalId agent : agents) {
            MindMapNode agentNode = overlaysByAgent.containsKey(agent.value())
                                    ? PerspectivalMerge.merge(sharedNode, overlaysByAgent.get(agent.value()))
                                    : sharedNode;

            List<String> entityIds      = collectEntityIds(agentNode);
            Set<NodeRef> unresolvedRefs = collectUnresolvedRefs(agentNode);

            List<MindMapEdge> edges = query.includeEdges()
                                      ? mindMapStore.neighbors(agentNode.id(), query.tenantId())
                                      : List.of();

            CognitiveProfileQuery           agentQuery = query.withAsSeenBy(agent);
            Map<MemoryDomain, List<Memory>> memories   = queryMemories(entityIds, domains, agentQuery);
            AffectTrajectory                trajectory = computeTrajectory(entityIds, memories, agentQuery);

            result.put(agent, new EntityKnowledge(
                    agentNode, edges, memories, trajectory, unresolvedRefs,
                    query.tenantId(), agent));
        }
        return result;
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
            var memQuery = MemoryQuery.forSubjects(
                                              entityIds.stream().map(id -> io.casehub.neocortex.memory.Subject.of("unknown", id)).toList(),
                                              domain, query.tenantId())
                                      .withLimit(query.memoryLimit())
                                      .withOrder(MemoryOrder.CHRONOLOGICAL);

            if (query.asSeenBy() != null) {
                memQuery = memQuery.withCallerPrincipalId(query.asSeenBy());
            }

            List<Memory> memories = memoryStore.query(memQuery);
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
            var memQuery = MemoryQuery.forSubjects(
                                              entityIds.stream().map(id -> io.casehub.neocortex.memory.Subject.of("unknown", id)).toList(),
                                              AffectEvents.DOMAIN, query.tenantId())
                                      .withLimit(query.memoryLimit())
                                      .withOrder(MemoryOrder.CHRONOLOGICAL);

            if (query.asSeenBy() != null) {
                memQuery = memQuery.withCallerPrincipalId(query.asSeenBy());
            }

            affectMemories = memoryStore.query(memQuery);
        }

        if (affectMemories.isEmpty()) {
            return null;
        }
        return AffectTrajectoryAnalyzer.analyze(affectMemories);
    }
}

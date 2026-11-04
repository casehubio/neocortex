package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.entity.token.EntityIdentity;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EntityIdentityCache {

    static final Set<String> ENTITY_SUBGRAPH_TYPES = Set.of(
        SubgraphTypes.PERSON, SubgraphTypes.PROJECT,
        SubgraphTypes.RESEARCH_AREA, SubgraphTypes.ORGANISATION,
        SubgraphTypes.CONCEPT, SubgraphTypes.ACTIVITY,
        SubgraphTypes.PLACE, SubgraphTypes.GENERAL);

    private static final Set<String> IDENTITY_KEYS = Set.of(
        "canonicalFull", "canonicalFirst", "canonicalLast", "canonicalTitlePrefix",
        "pronounsSubject", "pronounsObject", "pronounsPossessive", "pronounsReflexive",
        "preferredShort", "preferredLong", "preferredFormal", "preferredAbbreviation",
        "gender");

    private final MindMapStore store;
    private final ConcurrentHashMap<String, Map<String, EntityIdentity>> tenantCaches = new ConcurrentHashMap<>();

    public EntityIdentityCache(MindMapStore store) {
        this.store = store;
    }

    public EntityIdentity getIdentity(String entityId, String tenantId) {
        return tenantCaches
            .computeIfAbsent(tenantId, this::buildTenantCache)
            .get(entityId);
    }

    public Map<String, EntityIdentity> getAllIdentities(String tenantId) {
        return Map.copyOf(tenantCaches.computeIfAbsent(tenantId, this::buildTenantCache));
    }

    public void invalidate(String entityId, String tenantId) {
        var cache = tenantCaches.get(tenantId);
        if (cache == null) return;
        MindMapNode node = store.getNode(entityId, tenantId);
        if (node == null) {
            cache.remove(entityId);
        } else {
            EntityIdentity identity = buildIdentity(node);
            if (identity != null) {
                cache.put(entityId, identity);
            } else {
                cache.remove(entityId);
            }
        }
    }

    public void invalidateTenant(String tenantId) {
        tenantCaches.remove(tenantId);
    }

    private Map<String, EntityIdentity> buildTenantCache(String tenantId) {
        Map<String, EntityIdentity> cache          = new ConcurrentHashMap<>();
        Set<String>                 personaNodeIds = new java.util.HashSet<>();

        for (var subgraph : store.listSubgraphs(tenantId)) {
            if (!ENTITY_SUBGRAPH_TYPES.contains(subgraph.type())) {continue;}
            for (var node : store.nodesIn(subgraph.id(), tenantId)) {
                for (var edge : store.neighbors(node.id(), tenantId)) {
                    if ("persona-of".equals(edge.edgeType()) && edge.sourceNodeId().equals(node.id())) {
                        personaNodeIds.add(node.id());
                    }
                }
            }
        }

        for (var subgraph : store.listSubgraphs(tenantId)) {
            if (!ENTITY_SUBGRAPH_TYPES.contains(subgraph.type())) {continue;}
            for (var node : store.nodesIn(subgraph.id(), tenantId)) {
                if (personaNodeIds.contains(node.id())) {continue;}
                EntityIdentity baseIdentity = buildIdentity(node);
                if (baseIdentity == null) {continue;}

                Map<String, EntityIdentity> alterEgos = new LinkedHashMap<>();
                for (var edge : store.neighbors(node.id(), tenantId)) {
                    if ("persona-of".equals(edge.edgeType()) && edge.targetNodeId().equals(node.id())) {
                        MindMapNode personaNode = store.getNode(edge.sourceNodeId(), tenantId);
                        if (personaNode == null) {continue;}
                        String qualifier = personaNode.properties().get("personaQualifier");
                        if (qualifier == null || qualifier.isBlank()) {continue;}
                        EntityIdentity personaIdentity = buildIdentity(personaNode);
                        if (personaIdentity == null) {continue;}
                        alterEgos.put(qualifier, EntityIdentity.of(
                                node.id() + "." + qualifier, personaIdentity.canonicalFull(),
                                personaIdentity.nameParts(), personaIdentity.pronouns(),
                                personaIdentity.preferred(), personaIdentity.aliases()));
                    }
                }

                if (alterEgos.isEmpty()) {
                    cache.put(node.id(), baseIdentity);
                } else {
                    cache.put(node.id(), new EntityIdentity(
                            baseIdentity.entityId(), baseIdentity.canonicalFull(),
                            baseIdentity.nameParts(), baseIdentity.pronouns(),
                            baseIdentity.preferred(), baseIdentity.aliases(), alterEgos));
                }
            }
        }
        return cache;
    }

    static EntityIdentity buildIdentity(MindMapNode node) {
        String name = node.name();
        if (name == null || name.isBlank()) {return null;}

        Map<String, String> props     = node.properties();
        Map<String, String> nameParts = new LinkedHashMap<>();
        Map<String, String> pronouns  = new HashMap<>();
        Map<String, String> preferred = new HashMap<>();

        for (var entry : props.entrySet()) {
            String key   = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isBlank()) {continue;}

            if (key.startsWith("canonical") && !"canonicalFull".equals(key)) {
                nameParts.put(key, value);
            } else if (key.startsWith("pronouns")) {
                pronouns.put(key, value);
            } else if (key.startsWith("preferred")) {
                preferred.put(key, value);
            }
        }

        String canonicalFull = props.getOrDefault("canonicalFull", name);

        String aliasString = props.get("aliases");
        Set<String> aliases = (aliasString != null && !aliasString.isBlank())
                              ? Set.copyOf(java.util.Arrays.asList(aliasString.split(",")))
                              : Set.of();

        return EntityIdentity.of(node.id(), canonicalFull, nameParts, pronouns, preferred, aliases);
    }
}

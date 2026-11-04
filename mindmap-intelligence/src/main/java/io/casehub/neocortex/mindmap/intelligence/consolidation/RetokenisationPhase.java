package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.entity.token.EntityIdentity;
import io.casehub.neocortex.entity.token.EntityTokeniser;
import io.casehub.neocortex.entity.token.SurfaceForm;
import io.casehub.neocortex.entity.token.SurfaceFormTrie;
import io.casehub.neocortex.entity.token.TokenFormat;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryScanRequest;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.intelligence.SubgraphUtils;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(12)
public class RetokenisationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(RetokenisationPhase.class.getName());
    private static final String SENTINEL_NAME = "_retokenisation-state";
    private static final String CURSOR_PROPERTY = "retokenisation-cursor";
    private static final Set<String> ENTITY_SUBGRAPH_TYPES = Set.of(
        SubgraphTypes.PERSON, SubgraphTypes.PROJECT,
        SubgraphTypes.RESEARCH_AREA, SubgraphTypes.ORGANISATION,
        SubgraphTypes.CONCEPT, SubgraphTypes.ACTIVITY,
        SubgraphTypes.PLACE, SubgraphTypes.GENERAL);

    private final CaseMemoryStore memoryStore;
    private final MindMapStore mindMapStore;
    private final Function<String, SurfaceFormTrie> trieProvider;
    private final int batchSize;

    @Inject
    public RetokenisationPhase(
            Instance<CaseMemoryStore> memoryStore,
            Instance<MindMapStore> mindMapStore) {
        this.memoryStore = memoryStore.isResolvable() ? memoryStore.get() : null;
        MindMapStore ms = mindMapStore.isResolvable() ? mindMapStore.get() : null;
        this.mindMapStore = ms;
        this.trieProvider = ms != null ? tenantId -> buildTrieFromGraph(ms, tenantId) : t -> null;
        this.batchSize = 50;
    }

    RetokenisationPhase(CaseMemoryStore memoryStore, MindMapStore mindMapStore,
                        Function<String, SurfaceFormTrie> trieProvider, int batchSize) {
        this.memoryStore = memoryStore;
        this.mindMapStore = mindMapStore;
        this.trieProvider = trieProvider;
        this.batchSize = batchSize;
    }

    @Override
    public String name() {
        return "retokenisation";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (memoryStore == null || mindMapStore == null) {
            return;
        }
        SurfaceFormTrie trie = trieProvider.apply(tenantId);
        if (trie == null) {
            return;
        }

        String cursor = loadCursor(tenantId);
        List<Memory> memories = memoryStore.scan(
            new MemoryScanRequest(tenantId, null, null, null, batchSize, cursor));

        if (memories.isEmpty()) {
            return;
        }

        String lastProcessedId = cursor;
        int updated = 0;

        for (Memory memory : memories) {
            try {
                if (memory.text() == null || memory.text().isBlank()) {
                    lastProcessedId = memory.memoryId();
                    continue;
                }
                String plainText = TokenFormat.stripTokens(memory.text());
                String tokenised = EntityTokeniser.tokenise(plainText, trie);
                if (!tokenised.equals(memory.text())) {
                    memoryStore.updateText(memory.memoryId(), tokenised, tenantId);
                    updated++;
                }
                lastProcessedId = memory.memoryId();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to retokenise memory " + memory.memoryId(), e);
            }
        }

        if (lastProcessedId != null && !lastProcessedId.equals(cursor)) {
            saveCursor(tenantId, lastProcessedId);
        }

        if (updated > 0) {
            int count = updated;
            LOG.info(() -> "Retokenised " + count + " memories for tenant " + tenantId);
        }
    }

    private static SurfaceFormTrie buildTrieFromGraph(MindMapStore store, String tenantId) {
        List<SurfaceForm> allForms = new ArrayList<>();
        for (var subgraph : store.listSubgraphs(tenantId)) {
            if (!ENTITY_SUBGRAPH_TYPES.contains(subgraph.type())) continue;
            for (var node : store.nodesIn(subgraph.id(), tenantId)) {
                EntityIdentity identity = buildIdentity(node);
                if (identity != null) {
                    allForms.addAll(identity.surfaceForms());
                }
            }
        }
        return allForms.isEmpty() ? null : SurfaceFormTrie.build(allForms);
    }

    private static EntityIdentity buildIdentity(MindMapNode node) {
        String name = node.name();
        if (name == null || name.isBlank()) return null;

        Map<String, String> props = node.properties();
        Map<String, String> nameParts = new LinkedHashMap<>();
        Map<String, String> pronouns = new HashMap<>();
        Map<String, String> preferred = new HashMap<>();

        for (var entry : props.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isBlank()) continue;

            if (key.startsWith("canonical") && !"canonicalFull".equals(key)) {
                nameParts.put(key, value);
            } else if (key.startsWith("pronouns")) {
                pronouns.put(key, value);
            } else if (key.startsWith("preferred")) {
                preferred.put(key, value);
            }
        }

        String canonicalFull = props.getOrDefault("canonicalFull", name);
        return EntityIdentity.of(node.id(), canonicalFull, nameParts, pronouns, preferred, Set.of());
    }

    private String loadCursor(String tenantId) {
        return findSentinelNode(tenantId)
            .flatMap(n -> n.property(CURSOR_PROPERTY))
            .orElse(null);
    }

    private void saveCursor(String tenantId, String memoryId) {
        Optional<MindMapNode> sentinel = findSentinelNode(tenantId);
        if (sentinel.isPresent()) {
            mindMapStore.updateNode(sentinel.get().id(),
                NodeUpdate.empty().withPropertiesToSet(Map.of(CURSOR_PROPERTY, memoryId)),
                tenantId);
        } else {
            String sgId = SubgraphUtils.ensureSubgraph(mindMapStore, "Type System",
                SubgraphTypes.TYPE_SYSTEM, tenantId);
            mindMapStore.addNode(
                NodeInput.of(SENTINEL_NAME, sgId)
                    .withProperties(Map.of(CURSOR_PROPERTY, memoryId))
                    .withProvenance("retokenisation"),
                tenantId);
        }
    }

    private Optional<MindMapNode> findSentinelNode(String tenantId) {
        return mindMapStore.search(
                MindMapQuery.of(tenantId, 100).withType(SubgraphTypes.TYPE_SYSTEM))
            .stream()
            .filter(n -> SENTINEL_NAME.equals(n.name()))
            .findFirst();
    }
}

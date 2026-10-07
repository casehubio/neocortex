package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.Confidence;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryScanRequest;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.neocortex.mindmap.SubThoughtRef;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.intelligence.SubgraphUtils;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(16)
public class SubThoughtConsolidationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(SubThoughtConsolidationPhase.class.getName());
    private static final int DEFAULT_THRESHOLD = 3;
    private static final int MAX_PER_PASS = 50;

    private final CaseMemoryStore memoryStore;
    private final MindMapStore mindMapStore;
    private final int graduationThreshold;

    private final Map<EntityTypePair, List<SubThoughtSource>> accumulation = new ConcurrentHashMap<>();

    @Inject
    public SubThoughtConsolidationPhase(Instance<CaseMemoryStore> memoryStore,
                                        Instance<MindMapStore> mindMapStore) {
        this.memoryStore = memoryStore.isResolvable() ? memoryStore.get() : null;
        this.mindMapStore = mindMapStore.isResolvable() ? mindMapStore.get() : null;
        this.graduationThreshold = DEFAULT_THRESHOLD;
    }

    SubThoughtConsolidationPhase(CaseMemoryStore memoryStore,
                                 MindMapStore mindMapStore,
                                 int graduationThreshold) {
        this.memoryStore = memoryStore;
        this.mindMapStore = mindMapStore;
        this.graduationThreshold = graduationThreshold;
    }

    @Override
    public String name() { return "sub-thought-graduation"; }

    @Override
    public void beginTick() {}

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (memoryStore == null || mindMapStore == null) return;

        List<Memory> memories = memoryStore.scan(
            new MemoryScanRequest(tenantId, ExperienceEvents.DOMAIN.name(),
                null, null, MAX_PER_PASS, null));

        for (Memory memory : memories) {
            String countStr = memory.attributes().get(SubThoughtAttributeKeys.COUNT);
            if (countStr == null) continue;
            if ("biographical-import".equals(memory.attributes().get("provenance"))) continue;

            int count;
            try { count = Integer.parseInt(countStr); } catch (NumberFormatException e) { continue; }
            for (int i = 0; i < count; i++) {
                if ("true".equals(memory.attributes().get(SubThoughtAttributeKeys.graduated(i)))) continue;

                String entity = memory.attributes().get(SubThoughtAttributeKeys.entity(i));
                String type = memory.attributes().get(SubThoughtAttributeKeys.type(i));
                if (entity == null || type == null) continue;

                var key = new EntityTypePair(entity, type);
                accumulation.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new SubThoughtSource(memory.memoryId(), i));
            }
        }

        var graduated = new ArrayList<EntityTypePair>();
        for (var entry : accumulation.entrySet()) {
            if (entry.getValue().size() >= graduationThreshold) {
                graduateSubThought(entry.getKey(), entry.getValue(), tenantId);
                graduated.add(entry.getKey());
            }
        }
        graduated.forEach(accumulation::remove);
    }

    private void graduateSubThought(EntityTypePair pair, List<SubThoughtSource> sources,
                                     String tenantId) {
        String subgraphId = SubgraphUtils.ensureSubgraph(
            mindMapStore, SubgraphTypes.COGNITIVE, tenantId);

        Set<NodeRef> refs = new HashSet<>();
        for (var source : sources) {
            refs.add(SubThoughtRef.of(source.memoryId(), source.subThoughtIndex()));
        }

        mindMapStore.addNode(
            NodeInput.of(pair.entity() + " — " + pair.type(), subgraphId)
                .withConfidence(Confidence.inferred(0.7, Instant.now()))
                .withProvenance("sub-thought-graduation")
                .withTraits(Set.of("graduated-sub-thought"))
                .withRefs(refs)
                .withProperties(Map.of(
                    "cognitiveKind", pair.type(),
                    "source-count", String.valueOf(sources.size()))),
            tenantId);

        for (var source : sources) {
            try {
                memoryStore.enrichAttributes(source.memoryId(),
                    Map.of(SubThoughtAttributeKeys.graduated(source.subThoughtIndex()), "true"),
                    tenantId);
            } catch (Exception e) {
                LOG.warning("Could not mark sub-thought graduated: " + source.memoryId()
                    + "#" + source.subThoughtIndex());
            }
        }
    }

    record EntityTypePair(String entity, String type) {}
    record SubThoughtSource(String memoryId, int subThoughtIndex) {}
}

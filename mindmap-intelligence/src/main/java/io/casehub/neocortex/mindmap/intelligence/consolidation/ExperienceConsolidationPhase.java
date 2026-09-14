package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryScanRequest;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.GraduationClassifier;
import io.casehub.neocortex.memory.experience.GraduationResult;
import io.casehub.neocortex.memory.experience.GraduationScorer;
import io.casehub.neocortex.mindmap.MindMapConfidenceDefaults;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.MutationContext;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
@Priority(15)
public class ExperienceConsolidationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(
        ExperienceConsolidationPhase.class.getName());
    private static final String SENTINEL_NAME = "_consolidation-state";
    private static final String CURSOR_PROPERTY = "graduation-cursor";

    private final CaseMemoryStore memoryStore;
    private final MindMapStore mindMapStore;
    private final GraduationScorer scorer;
    private final GraduationClassifier classifier;
    private final double threshold;
    private final int maxPerPass;

    @Inject
    public ExperienceConsolidationPhase(
            CaseMemoryStore memoryStore,
            MindMapStore mindMapStore,
            Instance<GraduationScorer> scorer,
            Instance<GraduationClassifier> classifier,
            Instance<ExperienceConsolidationConfig> config) {
        this.memoryStore = memoryStore;
        this.mindMapStore = mindMapStore;
        this.scorer = scorer.isResolvable() ? scorer.get() : new DefaultGraduationScorer();
        this.classifier = classifier.isResolvable() ? classifier.get() : new DefaultGraduationClassifier();
        ExperienceConsolidationConfig c = config.isResolvable() ? config.get() : null;
        this.threshold = c != null ? c.threshold() : 0.5;
        this.maxPerPass = c != null ? c.maxPerPass() : 20;
    }

    ExperienceConsolidationPhase(CaseMemoryStore memoryStore,
                                  MindMapStore mindMapStore,
                                  GraduationScorer scorer,
                                  GraduationClassifier classifier,
                                  double threshold,
                                  int maxPerPass) {
        this.memoryStore = memoryStore;
        this.mindMapStore = mindMapStore;
        this.scorer = scorer;
        this.classifier = classifier;
        this.threshold = threshold;
        this.maxPerPass = maxPerPass;
    }

    @Override
    public String name() {
        return "experience-consolidation";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        String cursor = loadCursor(tenantId);

        List<Memory> experiences = memoryStore.scan(
            new MemoryScanRequest(tenantId,
                ExperienceEvents.DOMAIN.name(),
                null, null,
                maxPerPass,
                cursor));

        if (experiences.isEmpty()) return;

        String subgraphId = findOrCreateCognitiveSubgraph(tenantId);
        Set<String> existingSourceIds = loadExistingSourceMemoryIds(tenantId);
        String lastProcessedId = cursor;

        for (Memory memory : experiences) {
            try {
                double score = scorer.score(memory);
                if (score < threshold) {
                    lastProcessedId = memory.memoryId();
                    continue;
                }

                if (existingSourceIds.contains(memory.memoryId())) {
                    lastProcessedId = memory.memoryId();
                    continue;
                }

                GraduationResult result = classifier.classify(memory);

                Map<String, String> properties = new HashMap<>(result.properties());
                properties.put("source-memory-id", memory.memoryId());
                properties.put("graduation-score", String.valueOf(score));
                properties.put("event-type",
                    memory.attributes().getOrDefault(
                        ExperienceAttributeKeys.EVENT_TYPE, "unknown"));
                properties.put("agent-id", memory.subject().id());
                properties.put("cognitiveKind", result.cognitiveKind());

                String name = memory.text().length() > 100
                    ? memory.text().substring(0, 100) + "..."
                    : memory.text();

                NodeInput nodeInput = NodeInput.of(name, subgraphId)
                    .withConfidence(MindMapConfidenceDefaults.forOrigin(
                        result.confidenceOrigin(), Instant.now()))
                    .withProvenance("experience-consolidation")
                    .withProperties(properties);

                if (memory.pleasure() != null) nodeInput = nodeInput.withPleasure(memory.pleasure());
                if (memory.arousal() != null) nodeInput = nodeInput.withArousal(memory.arousal());
                if (memory.dominance() != null) nodeInput = nodeInput.withDominance(memory.dominance());

                mindMapStore.addNode(nodeInput, tenantId);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to graduate memory "
                    + memory.memoryId() + " for tenant " + tenantId, e);
            }
            lastProcessedId = memory.memoryId();
        }

        if (lastProcessedId != null && !lastProcessedId.equals(cursor)) {
            saveCursor(tenantId, lastProcessedId);
        }
    }

    private String findOrCreateCognitiveSubgraph(String tenantId) {
        return mindMapStore.listSubgraphs(tenantId).stream()
            .filter(sg -> SubgraphTypes.COGNITIVE.equals(sg.type()))
            .map(MindMapSubgraph::id)
            .findFirst()
            .orElseGet(() -> mindMapStore.createSubgraph(
                new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null),
                tenantId));
    }

    private Set<String> loadExistingSourceMemoryIds(String tenantId) {
        return mindMapStore.search(
                MindMapQuery.of(tenantId, 1000).withType(SubgraphTypes.COGNITIVE))
            .stream()
            .map(n -> n.property("source-memory-id"))
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());
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
            String sgId = findOrCreateTypeSystemSubgraph(tenantId);
            mindMapStore.addNode(
                NodeInput.of(SENTINEL_NAME, sgId)
                    .withProperties(Map.of(CURSOR_PROPERTY, memoryId))
                    .withProvenance("experience-consolidation"),
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

    private String findOrCreateTypeSystemSubgraph(String tenantId) {
        return mindMapStore.listSubgraphs(tenantId).stream()
            .filter(sg -> SubgraphTypes.TYPE_SYSTEM.equals(sg.type()))
            .map(MindMapSubgraph::id)
            .findFirst()
            .orElseGet(() -> mindMapStore.createSubgraph(
                new SubgraphInput("Type System", SubgraphTypes.TYPE_SYSTEM, null),
                tenantId));
    }
}

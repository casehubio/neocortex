package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.caps.BehavioralAttractor;
import io.casehub.neocortex.caps.CapsEngine;
import io.casehub.neocortex.caps.AgentCapsState;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationClassifier;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
@Priority(19)
public class BehavioralSynthesisPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(
        BehavioralSynthesisPhase.class.getName());
    private static final String CAPS_GENERATED_TRAIT = "CapsGenerated";
    private static final String SENTINEL_NAME = "_behavioral-synthesis-state";
    private static final String CURSOR_PROPERTY = "synthesis-cursor";

    private final MindMapStore store;
    private final CapsEngine capsEngine;
    private final SituationClassifier classifier;
    private final int maxPerPass;

    @Inject
    public BehavioralSynthesisPhase(MindMapStore store,
                                     Instance<CapsEngine> capsEngine,
                                     Instance<SituationClassifier> classifier) {
        this.store = store;
        this.capsEngine = capsEngine.isResolvable() ? capsEngine.get() : null;
        this.classifier = classifier.isResolvable() ? classifier.get() : null;
        this.maxPerPass = 20;
    }

    BehavioralSynthesisPhase(MindMapStore store,
                              CapsEngine capsEngine,
                              SituationClassifier classifier,
                              int maxPerPass) {
        this.store = store;
        this.capsEngine = capsEngine;
        this.classifier = classifier;
        this.maxPerPass = maxPerPass;
    }

    @Override
    public String name() {
        return "behavioral-synthesis";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (capsEngine == null || classifier == null) {
            LOG.fine("CAPS engine or classifier not available, skipping behavioral synthesis");
            return;
        }

        List<MindMapNode> graduated = findUnprocessedGraduated(tenantId);
        if (graduated.isEmpty()) return;

        Map<String, List<MindMapNode>> byAgent = graduated.stream()
            .filter(n -> n.property("agent-id").isPresent())
            .collect(Collectors.groupingBy(
                n -> n.property("agent-id").orElseThrow()));

        String behavioralSgId = findOrCreateBehavioralSubgraph(tenantId);
        Set<String> existingAttractors = loadExistingAttractorNodeIds(tenantId);
        String lastProcessedId = null;

        for (var agentEntry : byAgent.entrySet()) {
            String agentId = agentEntry.getKey();
            List<MindMapNode> agentNodes = agentEntry.getValue();

            AgentCapsState state = capsEngine.loadState(tenantId, agentId);
            if (state == null) {
                LOG.fine("No CAPS state for agent " + agentId + ", skipping");
                continue;
            }

            Map<String, Double> combinedActivations = new HashMap<>();

            for (MindMapNode node : agentNodes) {
                List<SituationActivation> activations = classifier.classify(
                    node.name(), nodeMetadata(node));

                for (SituationActivation sa : activations) {
                    combinedActivations.merge(sa.nodeId(), sa.confidence(), Math::max);
                }

                lastProcessedId = node.id();
            }

            if (combinedActivations.isEmpty()) continue;

            try {
                var result = capsEngine.settle(state, combinedActivations);

                for (BehavioralAttractor attractor : result.attractors()) {
                    createOrStrengthenAttractor(
                        tenantId, behavioralSgId, agentId, attractor,
                        existingAttractors);
                }

                AgentCapsState updated = capsEngine.updateWeights(
                    state, combinedActivations,
                    averageIntensity(agentNodes), averageValence(agentNodes),
                    1.0, "continuous");
                capsEngine.saveState(updated);

                for (MindMapNode node : agentNodes) {
                    store.updateNode(node.id(),
                        NodeUpdate.empty().withPropertiesToSet(
                            Map.of("caps-processed", "true")),
                        tenantId);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                    "CAPS settling failed for agent " + agentId, e);
            }
        }

        if (lastProcessedId != null) {
            saveCursor(tenantId, lastProcessedId);
        }
    }

    private List<MindMapNode> findUnprocessedGraduated(String tenantId) {
        List<MindMapNode> cognitive = store.search(
            MindMapQuery.of(tenantId, maxPerPass * 2)
                .withType(SubgraphTypes.COGNITIVE));

        return cognitive.stream()
            .filter(n -> n.property("source-memory-id").isPresent())
            .filter(n -> !n.property("caps-processed").isPresent())
            .limit(maxPerPass)
            .toList();
    }

    private void createOrStrengthenAttractor(String tenantId,
                                              String behavioralSgId,
                                              String agentId,
                                              BehavioralAttractor attractor,
                                              Set<String> existingAttractors) {
        Optional<MindMapNode> existing = store.search(
            MindMapQuery.of(tenantId, 100).withType(SubgraphTypes.BEHAVIORAL))
            .stream()
            .filter(n -> attractor.nodeId().equals(n.property("caps-node-id").orElse(null)))
            .filter(n -> agentId.equals(n.property("agent-id").orElse(null)))
            .findFirst();

        if (existing.isPresent()) {
            MindMapNode node = existing.get();
            double currentStrength = node.property("strength")
                .map(Double::parseDouble).orElse(0.0);
            double newStrength = Math.min(1.0,
                currentStrength * 0.7 + attractor.strength() * 0.3);

            store.updateNode(node.id(),
                NodeUpdate.empty().withPropertiesToSet(Map.of(
                    "strength", String.valueOf(newStrength),
                    "generation", String.valueOf(attractor.sourceGeneration()))),
                tenantId);
        } else {
            NodeInput input = NodeInput.of(attractor.nodeId(), behavioralSgId)
                .withTraits(Set.of(CAPS_GENERATED_TRAIT))
                .withProvenance("behavioral-synthesis")
                .withProperties(Map.of(
                    "caps-node-id", attractor.nodeId(),
                    "category", attractor.category(),
                    "strength", String.valueOf(attractor.strength()),
                    "agent-id", agentId,
                    "generation", String.valueOf(attractor.sourceGeneration())));
            store.addNode(input, tenantId);
            existingAttractors.add(attractor.nodeId());
        }
    }

    private Map<String, String> nodeMetadata(MindMapNode node) {
        Map<String, String> meta = new HashMap<>();
        node.property("event-type").ifPresent(v -> meta.put("event-type", v));
        node.property("cognitiveKind").ifPresent(v -> meta.put("cognitiveKind", v));
        return meta;
    }

    private double averageIntensity(List<MindMapNode> nodes) {
        return nodes.stream()
            .mapToDouble(n -> n.property("graduation-score")
                .map(Double::parseDouble).orElse(0.5))
            .average().orElse(0.5);
    }

    private double averageValence(List<MindMapNode> nodes) {
        return nodes.stream()
            .filter(n -> n.pleasure() != null)
            .mapToDouble(n -> n.pleasure())
            .average().orElse(0.0);
    }

    private Set<String> loadExistingAttractorNodeIds(String tenantId) {
        return store.search(
            MindMapQuery.of(tenantId, 500).withType(SubgraphTypes.BEHAVIORAL))
            .stream()
            .map(n -> n.property("caps-node-id"))
            .flatMap(Optional::stream)
            .collect(Collectors.toCollection(HashSet::new));
    }

    private String findOrCreateBehavioralSubgraph(String tenantId) {
        return store.listSubgraphs(tenantId).stream()
            .filter(sg -> SubgraphTypes.BEHAVIORAL.equals(sg.type()))
            .map(MindMapSubgraph::id)
            .findFirst()
            .orElseGet(() -> store.createSubgraph(
                new SubgraphInput("Behavioral", SubgraphTypes.BEHAVIORAL, null),
                tenantId));
    }

    private String loadCursor(String tenantId) {
        return findSentinelNode(tenantId)
            .flatMap(n -> n.property(CURSOR_PROPERTY))
            .orElse(null);
    }

    private void saveCursor(String tenantId, String nodeId) {
        Optional<MindMapNode> sentinel = findSentinelNode(tenantId);
        if (sentinel.isPresent()) {
            store.updateNode(sentinel.get().id(),
                NodeUpdate.empty().withPropertiesToSet(Map.of(CURSOR_PROPERTY, nodeId)),
                tenantId);
        } else {
            String sgId = findOrCreateTypeSystemSubgraph(tenantId);
            store.addNode(
                NodeInput.of(SENTINEL_NAME, sgId)
                    .withProperties(Map.of(CURSOR_PROPERTY, nodeId))
                    .withProvenance("behavioral-synthesis"),
                tenantId);
        }
    }

    private Optional<MindMapNode> findSentinelNode(String tenantId) {
        return store.search(
            MindMapQuery.of(tenantId, 100).withType(SubgraphTypes.TYPE_SYSTEM))
            .stream()
            .filter(n -> SENTINEL_NAME.equals(n.name()))
            .findFirst();
    }

    private String findOrCreateTypeSystemSubgraph(String tenantId) {
        return store.listSubgraphs(tenantId).stream()
            .filter(sg -> SubgraphTypes.TYPE_SYSTEM.equals(sg.type()))
            .map(MindMapSubgraph::id)
            .findFirst()
            .orElseGet(() -> store.createSubgraph(
                new SubgraphInput("Type System", SubgraphTypes.TYPE_SYSTEM, null),
                tenantId));
    }
}

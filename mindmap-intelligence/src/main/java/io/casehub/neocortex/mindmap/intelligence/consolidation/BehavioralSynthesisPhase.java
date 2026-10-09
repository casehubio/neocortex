package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.caps.AgentCapsState;
import io.casehub.neocortex.caps.BehavioralAttractor;
import io.casehub.neocortex.caps.CapsConnection;
import io.casehub.neocortex.caps.CapsEngine;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationClassifier;
import io.casehub.neocortex.cognitive.EmotionType;
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
    private final MindMapStore store;
    private final CapsEngine capsEngine;
    private final SituationClassifier classifier;
    private final int maxPerPass;
    private final io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry defaultsRegistry;


    @Inject
    public BehavioralSynthesisPhase(MindMapStore store,
                                    Instance<CapsEngine> capsEngine,
                                    Instance<SituationClassifier> classifier,
                                    Instance<io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry> defaultsRegistry) {
        this.store            = store;
        this.capsEngine       = capsEngine.isResolvable() ? capsEngine.get() : null;
        this.classifier       = classifier.isResolvable() ? classifier.get() : null;
        this.defaultsRegistry = defaultsRegistry.isResolvable() ? defaultsRegistry.get() : null;
        this.maxPerPass       = 20;
    }

    BehavioralSynthesisPhase(MindMapStore store,
                             CapsEngine capsEngine,
                             SituationClassifier classifier,
                             int maxPerPass) {
        this.store            = store;
        this.capsEngine       = capsEngine;
        this.classifier       = classifier;
        this.defaultsRegistry = null;
        this.maxPerPass       = maxPerPass;
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
        for (var agentEntry : byAgent.entrySet()) {
            String agentId = agentEntry.getKey();
            List<MindMapNode> agentNodes = agentEntry.getValue();

            AgentCapsState state = capsEngine.loadState(tenantId, agentId);
            if (state == null) {
                state = capsEngine.initializeAgent(tenantId, agentId,
                    new io.casehub.neocortex.cognitive.index.DispositionAxes(
                        "cooperative", "moderate", "calculated", "moderate", "cooperative"));
                LOG.info("Auto-initialized CAPS state for agent " + agentId);
            }

            Map<String, Double> experienceActivations = new HashMap<>();

            for (MindMapNode node : agentNodes) {
                List<SituationActivation> activations = classifier.classify(
                    node.name(), nodeMetadata(node));

                for (SituationActivation sa : activations) {
                    experienceActivations.merge(sa.nodeId(), sa.confidence(), Math::max);
                }

            }

            String dominantInput = experienceActivations.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

            Map<String, Double> combinedActivations = new HashMap<>(experienceActivations);
            mergeDriveIntensities(combinedActivations, agentId, tenantId);

            if (combinedActivations.isEmpty()) continue;

            try {
                var result = capsEngine.settle(state, combinedActivations);

                List<String> nodeNames = agentNodes.stream()
                    .map(MindMapNode::name).toList();

                io.casehub.neocortex.cognitive.index.ExpressionStyleDefaults expressionStyles = null;
                if (defaultsRegistry != null) {
                    var defaults = defaultsRegistry.forAgent(agentId);
                    if (defaults.isPresent() && defaults.get().expressionStyles() != null) {
                        expressionStyles = defaults.get().expressionStyles();
                    }
                }

                for (BehavioralAttractor attractor : result.attractors()) {
                    BehavioralAttractor enriched = attractor;
                    if (expressionStyles != null) {
                        String label = expressionStyles.expressionFor(attractor.category());
                        if (label != null) {
                            enriched = new BehavioralAttractor(attractor.category(),
                                    attractor.nodeId(), attractor.strength(),
                                    attractor.highSaturation(), attractor.sourceGeneration(), label);
                        }
                    }
                    createOrStrengthenAttractor(
                        tenantId, behavioralSgId, agentId, enriched,
                        existingAttractors, nodeNames, dominantInput);
                }

                AgentCapsState updated = state;

                List<MindMapNode> emotionNodes = agentNodes.stream()
                    .filter(n -> n.property("emotion-type").isPresent())
                    .toList();
                List<MindMapNode> regularNodes = agentNodes.stream()
                    .filter(n -> n.property("emotion-type").isEmpty())
                    .toList();

                if (!emotionNodes.isEmpty()) {
                    Map<String, Set<String>> tagIndex = buildTagSourceIndex();
                    for (MindMapNode eNode : emotionNodes) {
                        EmotionType eType = EmotionType.valueOf(
                            eNode.property("emotion-type").orElseThrow());
                        var signal = EmotionReinforcementMapper.forEmotion(eType);
                        double intensity = Double.parseDouble(
                            eNode.property("emotion-intensity").orElse("0.5"));
                        double arousal = eNode.arousal() != null
                            ? Math.abs(eNode.arousal()) : 0.0;
                        double salienceMultiplier = 1.0 + 0.3 * arousal;

                        Map<String, Double> emotionActivations = new HashMap<>();
                        for (String tag : signal.pathwayTags()) {
                            Set<String> sources = tagIndex.getOrDefault(tag, Set.of());
                            for (String src : sources) {
                                emotionActivations.put(src, 1.0);
                            }
                        }

                        if (!emotionActivations.isEmpty()) {
                            updated = capsEngine.updateWeights(updated,
                                emotionActivations, intensity,
                                signal.lambdaSign(), salienceMultiplier,
                                "continuous");
                        }
                    }
                }

                if (!regularNodes.isEmpty()) {
                    updated = capsEngine.updateWeights(updated,
                        combinedActivations,
                        averageIntensity(regularNodes),
                        averageValence(regularNodes),
                        1.0, "continuous");
                }

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

    }

    private List<MindMapNode> findUnprocessedGraduated(String tenantId) {
        List<MindMapNode> cognitive = store.search(
                MindMapQuery.of(tenantId, 2000)
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
                                             Set<String> existingAttractors,
                                             List<String> sourceNodeNames,
                                             String dominantInput) {
        Optional<MindMapNode> existing = store.search(
                                                      MindMapQuery.of(tenantId, 2000).withType(SubgraphTypes.BEHAVIORAL))
                                              .stream()
                                              .filter(n -> attractor.nodeId().equals(n.property("caps-node-id").orElse(null)))
                                              .filter(n -> agentId.equals(n.property("agent-id").orElse(null)))
                                              .filter(n -> dominantInput.equals(n.property("dominant-input").orElse("")))
                                              .findFirst();

        if (existing.isPresent()) {
            MindMapNode node = existing.get();
            double currentStrength = node.property("strength")
                                         .map(Double::parseDouble).orElse(0.0);
            int currentCount = node.property("source-count")
                                   .map(Integer::parseInt).orElse(0);
            double reinforcementBoost = Math.min(0.5, Math.log1p(currentCount) / Math.log1p(20));
            double boostedStrength    = Math.min(1.0, attractor.strength() * (1 + reinforcementBoost));
            double newStrength = Math.min(1.0,
                                          currentStrength * 0.5 + boostedStrength * 0.5);

            String currentNames = node.property("source-names").orElse("");

            List<String> namesList = new ArrayList<>(
                    currentNames.isEmpty() ? List.of() :
                    new ArrayList<>(List.of(currentNames.split(", "))));
            namesList.addAll(sourceNodeNames);
            while (namesList.size() > 5) {namesList.removeFirst();}
            String cappedNames = String.join(", ", namesList);

            var props = new HashMap<String, String>();
            props.put("strength", String.valueOf(newStrength));
            props.put("previous-strength", String.valueOf(currentStrength));
            props.put("generation", String.valueOf(attractor.sourceGeneration()));
            props.put("source-count", String.valueOf(currentCount + sourceNodeNames.size()));
            props.put("source-names", cappedNames);
            props.put("dominant-input", dominantInput);
            if (attractor.personalityLabel() != null) {
                props.put("personality-label", attractor.personalityLabel());
            }

            store.updateNode(node.id(),
                             NodeUpdate.empty().withPropertiesToSet(props),
                             tenantId);
        } else {
            String names = String.join(", ", sourceNodeNames);
            if (names.length() > 500) {names = names.substring(0, 500);}

            var props = new HashMap<String, String>();
            props.put("caps-node-id", attractor.nodeId());
            props.put("category", attractor.category());
            props.put("strength", String.valueOf(attractor.strength()));
            props.put("agent-id", agentId);
            props.put("generation", String.valueOf(attractor.sourceGeneration()));
            props.put("source-count", String.valueOf(sourceNodeNames.size()));
            props.put("source-names", names);
            props.put("dominant-input", dominantInput);
            if (attractor.personalityLabel() != null) {
                props.put("personality-label", attractor.personalityLabel());
            }

            store.addNode(
                    NodeInput.of(attractor.nodeId() + (dominantInput.isEmpty() ? "" : "-" + dominantInput), behavioralSgId)
                             .withTraits(Set.of(CAPS_GENERATED_TRAIT))
                             .withProperties(props),
                    tenantId);
        }
    }

    private Map<String, String> nodeMetadata(MindMapNode node) {
        Map<String, String> meta = new HashMap<>();
        node.property("event-type").ifPresent(v -> meta.put("event-type", v));
        node.property("cognitiveKind").ifPresent(v -> meta.put("cognitiveKind", v));
        node.property("salience-multiplier").ifPresent(v -> meta.put("salience-multiplier", v));
        node.property("developmental-period").ifPresent(v -> meta.put("developmental-period", v));
        node.property("situation-types").ifPresent(v -> meta.put("situation-types", v));
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

    private Map<String, Set<String>> buildTagSourceIndex() {
        var index = new HashMap<String, Set<String>>();
        for (CapsConnection conn : capsEngine.topology().connections()) {
            for (String tag : conn.tags()) {
                index.computeIfAbsent(tag, k -> new HashSet<>()).add(conn.from());
            }
        }
        return Map.copyOf(index);
    }


    private Set<String> loadExistingAttractorNodeIds(String tenantId) {
        return store.search(
            MindMapQuery.of(tenantId, 2000).withType(SubgraphTypes.BEHAVIORAL))
            .stream()
            .map(n -> n.property("caps-node-id"))
            .flatMap(Optional::stream)
            .collect(Collectors.toCollection(HashSet::new));
    }


    private void mergeDriveIntensities(Map<String, Double> combinedActivations,
                                       String agentId, String tenantId) {
        var cognitiveNodes = store.search(
                MindMapQuery.of(tenantId, 200).withType(SubgraphTypes.COGNITIVE));
        for (var node : cognitiveNodes) {
            if (!"drive-intensity".equals(node.properties().get("cognitiveKind"))) {continue;}
            if (!agentId.equals(node.properties().get("agent-id"))) {continue;}

            String description = node.properties().get("description");
            if (description == null || description.isBlank()) {continue;}

            double intensity;
            try { intensity = Double.parseDouble(node.properties().getOrDefault("intensity", "0.5")); }
            catch (NumberFormatException e) { intensity = 0.5; }

            var activations = classifier.classify(description, Map.of());
            for (var sa : activations) {
                double scaled = sa.confidence() * intensity;
                combinedActivations.merge(sa.nodeId(), scaled, Math::max);
            }
        }
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

}

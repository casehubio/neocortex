package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.inference.DenseEmbedder;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryScanRequest;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.GraduationClassifier;
import io.casehub.neocortex.memory.experience.GraduationContext;
import io.casehub.neocortex.memory.experience.GraduationResult;
import io.casehub.neocortex.memory.experience.GraduationScorer;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.MindMapConfidenceDefaults;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SignalCategory;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.intelligence.SubgraphUtils;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// Models sleep-dependent memory consolidation — Walker (2017), Stickgold & Walker (2013)
@ApplicationScoped
@Priority(15)
public class ExperienceConsolidationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(
        ExperienceConsolidationPhase.class.getName());
    private static final String SENTINEL_NAME = "_consolidation-state";
    private static final String CURSOR_PROPERTY = "graduation-cursor";
    private static final int    MAX_BLOCKED_TICKS = 10;
    private static final String BLOCKED_TICKS_PREFIX = "blocked-ticks:";


    private final CaseMemoryStore memoryStore;
    private final MindMapStore mindMapStore;
    private final List<AttentionSignal> pendingSignals = new ArrayList<>();
    private final GraduationScorer scorer;
    private final GraduationClassifier classifier;
    private final double threshold;
    private final int maxPerPass;
    private final int minCorroboration;
    private final TextSimilarityCorroborator textSimilarity;


    @Inject
    public ExperienceConsolidationPhase(
            CaseMemoryStore memoryStore,
            MindMapStore mindMapStore,
            Instance<GraduationScorer> scorer,
            Instance<GraduationClassifier> classifier,
            Instance<ExperienceConsolidationConfig> config,
            Instance<DenseEmbedder> embeddingModel) {
        this.memoryStore  = memoryStore;
        this.mindMapStore = mindMapStore;
        this.scorer       = scorer.isResolvable() ? scorer.get() : new DefaultGraduationScorer();
        this.classifier   = classifier.isResolvable() ? classifier.get() : new DefaultGraduationClassifier();
        ExperienceConsolidationConfig c = config.isResolvable() ? config.get() : null;
        this.threshold        = c != null ? c.threshold() : 0.5;
        this.maxPerPass       = c != null ? c.maxPerPass() : 20;
        this.minCorroboration = c != null ? c.minCorroboration() : 2;
        boolean tsEnabled = c != null && c.textSimilarity() != null && c.textSimilarity().enabled();
        if (tsEnabled) {
            double         embThreshold = c.textSimilarity().embeddingThreshold();
            double         kwThreshold  = c.textSimilarity().keywordThreshold();
            DenseEmbedder  model         = embeddingModel.isResolvable() ? embeddingModel.get() : null;
            this.textSimilarity = new TextSimilarityCorroborator(model, embThreshold, kwThreshold);
        } else {
            this.textSimilarity = null;
        }
    }

    ExperienceConsolidationPhase(CaseMemoryStore memoryStore,
                                  MindMapStore mindMapStore,
                                  GraduationScorer scorer,
                                  GraduationClassifier classifier,
                                  double threshold,
                                  int maxPerPass) {
        this(memoryStore, mindMapStore, scorer, classifier, threshold, maxPerPass, 2);
    }

    ExperienceConsolidationPhase(CaseMemoryStore memoryStore,
                                 MindMapStore mindMapStore,
                                 GraduationScorer scorer,
                                 GraduationClassifier classifier,
                                 double threshold,
                                 int maxPerPass,
                                 int minCorroboration) {
        this(memoryStore, mindMapStore, scorer, classifier, threshold, maxPerPass, minCorroboration, null);
    }

    ExperienceConsolidationPhase(CaseMemoryStore memoryStore,
                                 MindMapStore mindMapStore,
                                 GraduationScorer scorer,
                                 GraduationClassifier classifier,
                                 double threshold,
                                 int maxPerPass,
                                 int minCorroboration,
                                 TextSimilarityCorroborator textSimilarity) {
        this.memoryStore      = memoryStore;
        this.mindMapStore     = mindMapStore;
        this.scorer           = scorer;
        this.classifier       = classifier;
        this.threshold        = threshold;
        this.maxPerPass       = maxPerPass;
        this.minCorroboration = minCorroboration;
        this.textSimilarity   = textSimilarity;
    }


    @Override
    public String name() {
        return "experience-consolidation";
    }

    @Override
    public void beginTick() {
        pendingSignals.clear();
    }

    @Override
    public List<AttentionSignal> signals() {
        var result = List.copyOf(pendingSignals);
        pendingSignals.clear();
        return result;
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

        LOG.info("Experience consolidation: cursor=" + cursor + " found=" + experiences.size() + " memories for tenant=" + tenantId);
        if (experiences.isEmpty()) {return;}

        Map<String, String> agentSubgraphs    = new HashMap<>();
        Set<String>         existingSourceIds = loadExistingSourceMemoryIds(tenantId);
        Map<String, GraduationContext> corroborationMap =
                buildCorroborationMap(experiences, tenantId);
        String               lastProcessedId   = cursor;
        String               lowestBlockedId   = null;
        Map<String, Integer> blockedTickCounts = loadBlockedTickCounts(tenantId);

        List<NodeInput> graduatedInputs = new ArrayList<>();

        for (Memory memory : experiences) {
            try {
                String observed      = memory.attributes().get(ExperienceAttributeKeys.SUBJECT);
                String memoryAgentId = memory.subject() != null ? memory.subject().id() : null;
                GraduationContext context = observed != null
                                            ? withAgent(corroborationMap.getOrDefault(observed, new GraduationContext(0, tenantId)), memoryAgentId)
                                            : new GraduationContext(Integer.MAX_VALUE, 0, tenantId, memoryAgentId);
                double score = scorer.score(memory, context);
                if (score < threshold) {
                    boolean corroborationBlocked = observed != null
                                                   && context.corroboratingCount() < minCorroboration;
                    if (corroborationBlocked) {
                        int ticks = blockedTickCounts.getOrDefault(memory.memoryId(), 0) + 1;
                        if (ticks >= MAX_BLOCKED_TICKS) {
                            LOG.info("Abandoning permanently-blocked memory " + memory.memoryId()
                                     + " after " + ticks + " ticks for tenant=" + tenantId);
                            blockedTickCounts.remove(memory.memoryId());
                            lastProcessedId = memory.memoryId();
                        } else {
                            blockedTickCounts.put(memory.memoryId(), ticks);
                            if (lowestBlockedId == null) {
                                lowestBlockedId = memory.memoryId();
                            }
                        }
                    } else {
                        lastProcessedId = memory.memoryId();
                    }
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

                String agentId = memory.subject().id();
                String subgraphId = agentSubgraphs.computeIfAbsent(agentId,
                                                                   id -> SubgraphUtils.ensureNamedSubgraph(mindMapStore,
                                                                                                           "cognitive-" + id, SubgraphTypes.COGNITIVE, tenantId));

                String name = graduatedNodeName(result, memory);

                NodeInput nodeInput = NodeInput.of(name, subgraphId)
                                               .withConfidence(MindMapConfidenceDefaults.forOrigin(
                                                       result.confidenceOrigin(), Instant.now()))
                                               .withProvenance("experience-consolidation")
                                               .withProperties(properties);

                if (memory.pleasure() != null) {nodeInput = nodeInput.withPleasure(memory.pleasure());}
                if (memory.arousal() != null) {nodeInput = nodeInput.withArousal(memory.arousal());}
                if (memory.dominance() != null) {nodeInput = nodeInput.withDominance(memory.dominance());}

                graduatedInputs.add(nodeInput);
                blockedTickCounts.remove(memory.memoryId());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to graduate memory "
                                       + memory.memoryId() + " for tenant " + tenantId, e);
            }
            lastProcessedId = memory.memoryId();
        }

        // Watermark: cursor must not advance past blocked memories
        if (lowestBlockedId != null && (lastProcessedId == null || lowestBlockedId.compareTo(lastProcessedId) < 0)) {
            lastProcessedId = cursor;
        }

        LOG.info("Experience consolidation: " + graduatedInputs.size() + " memories graduated for tenant=" + tenantId);
        if (!graduatedInputs.isEmpty()) {
            mindMapStore.addNodes(graduatedInputs, tenantId);
            for (NodeInput input : graduatedInputs) {
                pendingSignals.add(new AttentionSignal(
                        input.properties().get("agent-id"), tenantId,
                        SignalCategory.EXPERIENCE_GRADUATED,
                        null, input.name(), 0.6,
                        "experience graduated to " + input.properties().getOrDefault("cognitiveKind", "unknown")));
            }
        }

        if (lastProcessedId != null && !lastProcessedId.equals(cursor)) {
            saveCursor(tenantId, lastProcessedId);
        }
        saveBlockedTickCounts(tenantId, blockedTickCounts);
    }


    private Map<String, Integer> loadBlockedTickCounts(String tenantId) {
        var result = new HashMap<String, Integer>();
        findSentinelNode(tenantId).ifPresent(sentinel -> {
            for (var entry : sentinel.properties().entrySet()) {
                if (entry.getKey().startsWith(BLOCKED_TICKS_PREFIX)) {
                    String memoryId = entry.getKey().substring(BLOCKED_TICKS_PREFIX.length());
                    try {
                        result.put(memoryId, Integer.parseInt(entry.getValue()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
        return result;
    }

    private void saveBlockedTickCounts(String tenantId, Map<String, Integer> counts) {
        Optional<MindMapNode> sentinel = findSentinelNode(tenantId);
        if (sentinel.isEmpty()) {
            if (counts.isEmpty()) return;
            String sgId = findOrCreateTypeSystemSubgraph(tenantId);
            var props = new HashMap<String, String>();
            for (var entry : counts.entrySet()) {
                props.put(BLOCKED_TICKS_PREFIX + entry.getKey(), String.valueOf(entry.getValue()));
            }
            mindMapStore.addNode(
                NodeInput.of(SENTINEL_NAME, sgId)
                    .withProperties(props)
                    .withProvenance("experience-consolidation"),
                tenantId);
            return;
        }
        var propsToSet    = new HashMap<String, String>();
        var propsToRemove = new java.util.HashSet<String>();
        for (var entry : sentinel.get().properties().entrySet()) {
            if (entry.getKey().startsWith(BLOCKED_TICKS_PREFIX)) {
                String memoryId = entry.getKey().substring(BLOCKED_TICKS_PREFIX.length());
                if (!counts.containsKey(memoryId)) {
                    propsToRemove.add(entry.getKey());
                }
            }
        }
        for (var entry : counts.entrySet()) {
            propsToSet.put(BLOCKED_TICKS_PREFIX + entry.getKey(), String.valueOf(entry.getValue()));
        }
        if (!propsToSet.isEmpty() || !propsToRemove.isEmpty()) {
            NodeUpdate update = NodeUpdate.empty();
            if (!propsToSet.isEmpty()) {update = update.withPropertiesToSet(propsToSet);}
            if (!propsToRemove.isEmpty()) {update = update.withPropertiesToRemove(propsToRemove);}
            mindMapStore.updateNode(sentinel.get().id(), update, tenantId);
        }
    }

    static String graduatedNodeName(GraduationResult result, Memory memory) {
        String rawText = memory.text() != null ? memory.text() : "";
        return switch (result.cognitiveKind()) {
            case "belief" -> {
                String subject = result.properties().getOrDefault("subject", "");
                yield subject.isEmpty()
                      ? truncate("Belief: " + rawText, 70)
                      : truncate("Belief: " + subject + " — " + rawText, 70);
            }
            case "intention" -> {
                String capability = result.properties().getOrDefault("goal", "");
                yield capability.isEmpty()
                      ? truncate("Intention: " + rawText, 70)
                      : truncate("Intention: " + capability + " — " + rawText, 70);
            }
            case "judgment" -> {
                String target = result.properties().getOrDefault("target", "");
                yield target.isEmpty()
                      ? truncate("Judgment: " + rawText, 70)
                      : truncate("Judgment: " + target + " — " + rawText, 70);
            }
            case "formative-experience" -> truncate("Formative: " + rawText, 92);
            default -> truncate(rawText, 100);
        };
    }

    private static String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private Map<String, GraduationContext> buildCorroborationMap(
            List<Memory> experiences, String tenantId) {
        Map<String, GraduationContext> map = new HashMap<>();
        for (Memory m : experiences) {
            String observed = m.attributes().get(ExperienceAttributeKeys.SUBJECT);
            if (observed == null || map.containsKey(observed)) {continue;}
            var scan = new MemoryScanRequest(tenantId,
                                             ExperienceEvents.DOMAIN.name(),
                                             ExperienceAttributeKeys.SUBJECT, observed,
                                             minCorroboration, null);
            int count        = memoryStore.scan(scan).size();
            int textSimCount = 0;
            if (textSimilarity != null && count < minCorroboration) {
                textSimCount = textSimilarity.countSimilar(m, experiences);
            }
            map.put(observed, new GraduationContext(count, textSimCount, tenantId));
        }
        return map;
    }

    private Set<String> loadExistingSourceMemoryIds(String tenantId) {
        return mindMapStore.search(
                MindMapQuery.of(tenantId, 2000).withType(SubgraphTypes.COGNITIVE))
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
        return SubgraphUtils.ensureSubgraph(mindMapStore, "Type System", SubgraphTypes.TYPE_SYSTEM, tenantId);
    }

    private static GraduationContext withAgent(GraduationContext ctx, String agentId) {
        return new GraduationContext(ctx.corroboratingCount(), ctx.textSimilarityCount(), ctx.tenantId(), agentId);
    }
}

package io.casehub.neocortex.cognition.dissonance;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.session.ParallelLlmExecutor;
import io.casehub.platform.agent.session.SessionPipeline;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(17)
public class DissonanceDetectionPhase implements ConsolidationPhase, DissonanceActivationSource {

    private static final Logger LOG                  = Logger.getLogger(DissonanceDetectionPhase.class.getName());
    private static final String TENSION_TRAIT        = "DissonanceTension";
    private static final String BELIEF_TRAIT         = "Belieflike";
    private static final String CAPS_GENERATED_TRAIT = "CapsGenerated";

    private static final String SYSTEM_PROMPT = """
                                                You are analyzing a character's beliefs and behavioral patterns for \
                                                cognitive dissonance — contradictions between what they believe and \
                                                what they do, or between beliefs that cannot both be true.
                                                For each contradiction found, provide the CAPS input node activations \
                                                that each element would trigger (using node IDs from the CAPS topology: \
                                                secure_attachment, rejection, mastery, failure, acceptance, exclusion, \
                                                dominance, submission, reward, punishment, etc.).
                                                Respond with JSON only. If no contradictions found, return \
                                                {"contradictions": []}.
                                                """;

    private final    MindMapStore                                     store;
    private final    DissonanceDetectionConfig                        config;
    private volatile SessionPipeline                                  sessionPipeline;
    private final    ConcurrentHashMap<String, List<TensionSnapshot>> cache = new ConcurrentHashMap<>();

    @Inject
    public DissonanceDetectionPhase(MindMapStore store,
                                    Instance<DissonanceDetectionConfig> config) {
        this(store, config.isResolvable() ? config.get() : DissonanceDetectionConfig.defaults());
    }

    DissonanceDetectionPhase(MindMapStore store, DissonanceDetectionConfig config) {
        this.store  = store;
        this.config = config;
    }

    @Override
    public String name() {return "dissonance-detection";}

    @Override
    public void setSessionPipeline(SessionPipeline pipeline) {
        this.sessionPipeline = pipeline;
    }

    public DissonanceDetectionConfig config() {return config;}

    public List<TensionSnapshot> activeTensionSnapshots(String agentId, String tenantId) {
        return cache.getOrDefault(tenantId + ":" + agentId, List.of());
    }

    @Override
    public List<TensionActivation> activeTensions(String cacheKey) {
        var snapshots = cache.getOrDefault(cacheKey, List.of());
        return snapshots.stream()
                        .map(s -> new TensionActivation(s.capsActivationsA(), s.capsActivationsB(), s.strength()))
                        .toList();
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (!config.enabled() || sessionPipeline == null) {return;}

        var subgraphs = store.listSubgraphs(tenantId);
        var cognitiveSubgraphs = subgraphs.stream()
                                          .filter(sg -> SubgraphTypes.COGNITIVE.equals(sg.type()))
                                          .toList();
        if (cognitiveSubgraphs.isEmpty()) {return;}

        var beliefs          = new HashMap<String, List<MindMapNode>>();
        var attractors       = new HashMap<String, List<MindMapNode>>();
        var existingTensions = new HashMap<String, List<MindMapNode>>();

        for (var sg : cognitiveSubgraphs) {
            for (var node : store.nodesIn(sg.id(), tenantId)) {
                var agentId = node.properties().get("agent-id");
                if (agentId == null) {continue;}

                if (node.traits().contains(TENSION_TRAIT)) {
                    existingTensions.computeIfAbsent(agentId, k -> new ArrayList<>()).add(node);
                } else if (node.traits().contains(BELIEF_TRAIT)) {
                    beliefs.computeIfAbsent(agentId, k -> new ArrayList<>()).add(node);
                }
            }
        }

        var behavioralSubgraphs = subgraphs.stream()
                                           .filter(sg -> SubgraphTypes.BEHAVIORAL.equals(sg.type()))
                                           .toList();
        for (var sg : behavioralSubgraphs) {
            for (var node : store.nodesIn(sg.id(), tenantId)) {
                if (node.traits().contains(CAPS_GENERATED_TRAIT)) {
                    var agentId = node.properties().get("agent-id");
                    if (agentId != null) {
                        attractors.computeIfAbsent(agentId, k -> new ArrayList<>()).add(node);
                    }
                }
            }
        }

        if (beliefs.isEmpty()) {return;}

        for (var agentId : beliefs.keySet()) {
            try {
                processAgent(agentId, beliefs.get(agentId),
                             attractors.getOrDefault(agentId, List.of()),
                             existingTensions.getOrDefault(agentId, List.of()),
                             tenantId, cognitiveSubgraphs.getFirst().id());
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        agentId + ": dissonance detection failed (non-fatal)", e);
            }
        }
    }

    private void processAgent(String agentId, List<MindMapNode> agentBeliefs,
                              List<MindMapNode> agentAttractors,
                              List<MindMapNode> agentTensions,
                              String tenantId, String cognitiveSubgraphId) {
        var detected = detectContradictions(agentId, agentBeliefs, agentAttractors);

        var tensionsByKey = new HashMap<String, MindMapNode>();
        for (var t : agentTensions) {
            var key = dedupKey(
                    t.property("element-a-id").orElse(""),
                    t.property("element-b-id").orElse(""));
            tensionsByKey.put(key, t);
        }

        var confirmedKeys = new java.util.HashSet<String>();
        for (var c : detected) {
            var key = dedupKey(c.elementAId(), c.elementBId());
            confirmedKeys.add(key);
            var existing = tensionsByKey.get(key);
            if (existing != null) {
                confirmTension(existing, c, tenantId);
            } else {
                createTension(c, agentId, tenantId, cognitiveSubgraphId);
            }
        }

        for (var entry : tensionsByKey.entrySet()) {
            if (!confirmedKeys.contains(entry.getKey())) {
                decayTension(entry.getValue(), tenantId);
            }
        }

        updateCache(agentId, tenantId, cognitiveSubgraphId);
    }

    private List<DetectedContradiction> detectContradictions(String agentId,
                                                             List<MindMapNode> beliefs,
                                                             List<MindMapNode> attractors) {
        var beliefsText = new StringBuilder();
        for (int i = 0; i < beliefs.size(); i++) {
            var b = beliefs.get(i);
            beliefsText.append(String.format("%d. [%s] \"%s\"\n", i + 1, b.id(), b.name()));
        }

        var attractorsText = new StringBuilder();
        for (int i = 0; i < attractors.size(); i++) {
            var a             = attractors.get(i);
            var category      = a.property("category").orElse(a.name());
            var dominantInput = a.property("dominant-input").orElse("");
            var desc          = category;
            if (!dominantInput.isEmpty()) {
                desc += " (driven by " + dominantInput + ")";
            }
            attractorsText.append(String.format("%d. [%s] \"%s\"\n", i + 1, a.id(), desc));
        }

        var userPrompt = String.format("""
                                       Character: %s
                                       
                                       Current beliefs:
                                       %s
                                       Behavioral patterns:
                                       %s
                                       Identify contradictions between beliefs that cannot both be true, \
                                       or between a belief and an established behavioral pattern. \
                                       For each contradiction provide JSON with elementA, elementB, \
                                       strength (0.0-1.0), capsActivationsA, capsActivationsB, and reasoning.""",
                                       agentId, beliefsText, attractorsText);

        var responseText = new StringBuilder();
        ParallelLlmExecutor.execute(sessionPipeline, "consolidation", 1, SYSTEM_PROMPT,
                                    "dissonance-detection",
                                    List.of(session -> {
                                        session.query(userPrompt).subscribe().asStream()
                                               .filter(e -> e instanceof AgentEvent.TextDelta)
                                               .map(e -> ((AgentEvent.TextDelta) e).text())
                                               .forEach(responseText::append);
                                    }));

        return parseContradictions(responseText.toString());
    }

    private void createTension(DetectedContradiction c, String agentId,
                               String tenantId, String cognitiveSubgraphId) {
        var now   = Instant.now().toString();
        var props = new HashMap<String, String>();
        props.put("tension-type", c.tensionType());
        props.put("element-a-id", c.elementAId());
        props.put("element-b-id", c.elementBId());
        props.put("element-a-text", c.elementAText());
        props.put("element-b-text", c.elementBText());
        props.put("strength", String.valueOf(c.strength()));
        props.put("caps-activations-a", c.capsActivationsA());
        props.put("caps-activations-b", c.capsActivationsB());
        props.put("reasoning", c.reasoning());
        props.put("agent-id", agentId);
        props.put("detected-at", now);
        props.put("last-confirmed", now);
        props.put("decay-count", "0");

        store.addNode(
                NodeInput.of(c.elementAText() + " vs " + c.elementBText(), cognitiveSubgraphId)
                         .withTraits(Set.of(TENSION_TRAIT))
                         .withProvenance("dissonance-detection")
                         .withProperties(props),
                tenantId);
    }

    private void confirmTension(MindMapNode existing, DetectedContradiction c,
                                String tenantId) {
        store.updateNode(existing.id(),
                         NodeUpdate.empty().withPropertiesToSet(Map.of(
                                 "last-confirmed", Instant.now().toString(),
                                 "decay-count", "0",
                                 "strength", String.valueOf(c.strength()))),
                         tenantId);
    }

    private void decayTension(MindMapNode tension, String tenantId) {
        double currentStrength = Double.parseDouble(
                tension.property("strength").orElse("0"));
        double newStrength = currentStrength - config.decayRate();
        int decayCount = Integer.parseInt(
                tension.property("decay-count").orElse("0")) + 1;

        if (newStrength < config.resolutionThreshold()) {
            store.supersede(tension.id(), null,
                            "dissonance resolved: strength decayed below threshold", tenantId);
        } else {
            store.updateNode(tension.id(),
                             NodeUpdate.empty().withPropertiesToSet(Map.of(
                                     "strength", String.valueOf(newStrength),
                                     "decay-count", String.valueOf(decayCount))),
                             tenantId);
        }
    }

    private void updateCache(String agentId, String tenantId, String cognitiveSubgraphId) {
        var snapshots = new ArrayList<TensionSnapshot>();
        for (var node : store.nodesIn(cognitiveSubgraphId, tenantId)) {
            if (node.traits().contains(TENSION_TRAIT)
                && agentId.equals(node.properties().get("agent-id"))) {
                snapshots.add(new TensionSnapshot(
                        node.id(),
                        node.property("tension-type").orElse(""),
                        node.property("element-a-id").orElse(""),
                        node.property("element-b-id").orElse(""),
                        node.property("element-a-text").orElse(""),
                        node.property("element-b-text").orElse(""),
                        Double.parseDouble(node.property("strength").orElse("0")),
                        node.property("reasoning").orElse(""),
                        parseCapsActivations(node.property("caps-activations-a").orElse("")),
                        parseCapsActivations(node.property("caps-activations-b").orElse(""))));
            }
        }
        cache.put(tenantId + ":" + agentId, List.copyOf(snapshots));
    }

    private static Map<String, Double> parseCapsActivations(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {return Map.of();}
        try {
            var result   = new HashMap<String, Double>();
            var stripped = json.strip();
            if (stripped.startsWith("{")) {stripped = stripped.substring(1);}
            if (stripped.endsWith("}")) {stripped = stripped.substring(0, stripped.length() - 1);}
            for (var pair : stripped.split(",")) {
                var parts = pair.split(":");
                if (parts.length == 2) {
                    var key = parts[0].strip().replace("\"", "");
                    var val = parts[1].strip().replace("\"", "");
                    if (!key.isEmpty()) {
                        result.put(key, Double.parseDouble(val));
                    }
                }
            }
            return Map.copyOf(result);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String dedupKey(String idA, String idB) {
        return idA.compareTo(idB) <= 0 ? idA + ":" + idB : idB + ":" + idA;
    }

    record DetectedContradiction(
            String elementAId, String elementAText, String elementAType,
            String elementBId, String elementBText, String elementBType,
            double strength,
            String capsActivationsA, String capsActivationsB,
            String reasoning
    ) {
        String tensionType() {
            if ("belief".equals(elementAType) && "belief".equals(elementBType)) {
                return "belief-belief";
            }
            return "belief-behavior";
        }
    }

    private List<DetectedContradiction> parseContradictions(String json) {
        json = json.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
        }
        var results    = new ArrayList<DetectedContradiction>();
        int searchFrom = 0;
        while (true) {
            int idx = json.indexOf("\"elementA\"", searchFrom);
            if (idx < 0) {break;}

            int objStart = json.lastIndexOf('{', idx);
            int objEnd   = findMatchingBrace(json, objStart);
            if (objEnd < 0) {break;}
            String obj = json.substring(objStart, objEnd + 1);
            searchFrom = objEnd + 1;

            var aId       = extractNestedField(obj, "elementA", "nodeId");
            var aText     = extractNestedField(obj, "elementA", "text");
            var aType     = extractNestedField(obj, "elementA", "type");
            var bId       = extractNestedField(obj, "elementB", "nodeId");
            var bText     = extractNestedField(obj, "elementB", "text");
            var bType     = extractNestedField(obj, "elementB", "type");
            var strength  = extractDoubleField(obj, "strength");
            var capsA     = extractObjectField(obj, "capsActivationsA");
            var capsB     = extractObjectField(obj, "capsActivationsB");
            var reasoning = extractField(obj, "reasoning");

            if (aId != null && bId != null && strength > 0) {
                results.add(new DetectedContradiction(
                        aId, aText != null ? aText : "", aType != null ? aType : "belief",
                        bId, bText != null ? bText : "", bType != null ? bType : "belief",
                        strength,
                        capsA != null ? capsA : "{}",
                        capsB != null ? capsB : "{}",
                        reasoning != null ? reasoning : "LLM-detected contradiction"));
            }
        }
        return results;
    }

    private static int findMatchingBrace(String json, int openBrace) {
        int     depth    = 0;
        boolean inString = false;
        for (int i = openBrace; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && inString) {
                i++;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {continue;}
            if (c == '{') {depth++;}
            if (c == '}') {
                depth--;
                if (depth == 0) {return i;}
            }
        }
        return -1;
    }

    private static String extractNestedField(String json, String parent, String child) {
        int parentIdx = json.indexOf("\"" + parent + "\"");
        if (parentIdx < 0) {return null;}
        int braceStart = json.indexOf('{', parentIdx);
        if (braceStart < 0) {return null;}
        int braceEnd = findMatchingBrace(json, braceStart);
        if (braceEnd < 0) {return null;}
        String nested = json.substring(braceStart, braceEnd + 1);
        return extractField(nested, child);
    }

    private static String extractObjectField(String json, String field) {
        int start = json.indexOf("\"" + field + "\"");
        if (start < 0) {return null;}
        int colon = json.indexOf(':', start);
        if (colon < 0) {return null;}
        int braceStart = json.indexOf('{', colon);
        if (braceStart < 0) {return null;}
        int braceEnd = findMatchingBrace(json, braceStart);
        if (braceEnd < 0) {return null;}
        return json.substring(braceStart, braceEnd + 1);
    }

    private static String extractField(String json, String field) {
        int start = json.indexOf("\"" + field + "\"");
        if (start < 0) {return null;}
        int colon = json.indexOf(':', start);
        if (colon < 0) {return null;}
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) {return null;}
        quote++;
        var sb = new StringBuilder();
        for (int i = quote; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(++i));
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static double extractDoubleField(String json, String field) {
        int start = json.indexOf("\"" + field + "\"");
        if (start < 0) {return 0.0;}
        int colon = json.indexOf(':', start);
        if (colon < 0) {return 0.0;}
        var numStr = new StringBuilder();
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']') {break;}
            if (!Character.isWhitespace(c)) {numStr.append(c);}
        }
        try {
            return Double.parseDouble(numStr.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}

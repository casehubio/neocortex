package io.casehub.neocortex.cognition.belief;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.session.ParallelLlmExecutor;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.platform.api.identity.PrincipalId;
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
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(14)
public class BeliefRevisionPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(BeliefRevisionPhase.class.getName());
    private static final String CURSOR_NODE_NAME = "belief-revision-cursor";
    private static final String BELIEF_TRAIT = "Belieflike";
    private static final String EXPERIENCE_PROVENANCE = "experience-consolidation";
    private static final String REVISION_PROVENANCE = "belief-revision";

    private static final String SYSTEM_PROMPT = """
            You are analyzing a character's beliefs against recent evidence from their experiences.
            For each belief that is contradicted by the evidence, identify the contradiction.
            Respond with JSON only. If no contradictions are found, return {"contradictions": []}.
            """;

    private final MindMapStore mindMapStore;
    private final BeliefRevisionConfig config;
    private volatile SessionPipeline sessionPipeline;

    @Inject
    public BeliefRevisionPhase(MindMapStore mindMapStore,
                               Instance<BeliefRevisionConfig> config) {
        this(mindMapStore,
             config.isResolvable() ? config.get() : BeliefRevisionConfig.defaults());
    }

    BeliefRevisionPhase(MindMapStore mindMapStore, BeliefRevisionConfig config) {
        this.mindMapStore = mindMapStore;
        this.config = config;
    }

    @Override
    public void setSessionPipeline(SessionPipeline pipeline) {
        this.sessionPipeline = pipeline;
    }

    @Override
    public String name() {
        return "belief-revision";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (!config.enabled()) {return;}
        if (sessionPipeline == null) {return;}
        var subgraphs = mindMapStore.listSubgraphs(tenantId);
        var cognitiveSubgraphs = subgraphs.stream()
                                          .filter(sg -> "cognitive".equals(sg.type()))
                                          .toList();
        if (cognitiveSubgraphs.isEmpty()) {return;}

        var allBeliefs  = new HashMap<String, List<MindMapNode>>();
        var allEvidence = new HashMap<String, List<MindMapNode>>();
        var cursorNodes = new HashMap<String, CursorInfo>();

        for (var sg : cognitiveSubgraphs) {
            var nodes = mindMapStore.nodesIn(sg.id(), tenantId);
            for (var node : nodes) {
                if (node.name() != null && node.name().startsWith(CURSOR_NODE_NAME)) {
                    var agentKey = node.name().length() > CURSOR_NODE_NAME.length() + 1
                                   ? node.name().substring(CURSOR_NODE_NAME.length() + 1) : null;
                    var     tsStr = node.properties().get("last-processed-timestamp");
                    Instant ts    = tsStr != null ? Instant.parse(tsStr) : null;
                    cursorNodes.put(agentKey != null ? agentKey : "", new CursorInfo(sg.id(), node.id(), ts));
                    continue;
                }
                if (node.traits().contains(BELIEF_TRAIT)) {
                    var agentId = node.principalId() != null ? node.principalId().id() : null;
                    if (agentId != null) {
                        allBeliefs.computeIfAbsent(agentId, k -> new ArrayList<>()).add(node);
                    }
                } else if (EXPERIENCE_PROVENANCE.equals(node.provenance())) {
                    var agentId = node.properties().get("agent-id");
                    if (agentId != null) {
                        allEvidence.computeIfAbsent(agentId, k -> new ArrayList<>()).add(node);
                    }
                }
            }
        }

        if (allBeliefs.isEmpty()) {return;}

        for (var agentId : allBeliefs.keySet()) {
            var     beliefs     = allBeliefs.get(agentId);
            var     evidence    = allEvidence.getOrDefault(agentId, List.of());
            var     cursorInfo  = cursorNodes.get(agentId);
            Instant agentCursor = cursorInfo != null ? cursorInfo.lastProcessedAt : null;
            var     newEvidence = filterNewEvidence(evidence, agentCursor);
            if (newEvidence.isEmpty()) {continue;}

            try {
                processAgent(agentId, beliefs, newEvidence, tenantId);
                Instant latestTimestamp = agentCursor;
                for (var ev : newEvidence) {
                    if (ev.createdAt() != null && (latestTimestamp == null || ev.createdAt().isAfter(latestTimestamp))) {
                        latestTimestamp = ev.createdAt();
                    }
                }
                if (latestTimestamp != null && !latestTimestamp.equals(agentCursor)) {
                    var cursorName = CURSOR_NODE_NAME + ":" + agentId;
                    if (cursorInfo != null) {
                        saveCursor(cursorInfo.subgraphId, cursorInfo.subgraphId,
                                   cursorInfo.nodeId, latestTimestamp, tenantId);
                    } else {
                        saveCursor(cognitiveSubgraphs.get(0).id(), null,
                                   null, latestTimestamp, tenantId, cursorName);
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, agentId + ": belief revision failed, cursor not advanced", e);
            }
        }
    }

    private List<MindMapNode> filterNewEvidence(List<MindMapNode> evidence, Instant lastProcessedAt) {
        if (lastProcessedAt == null) return evidence;
        return evidence.stream()
            .filter(n -> n.createdAt() != null && n.createdAt().isAfter(lastProcessedAt))
            .toList();
    }

    private boolean processAgent(String agentId, List<MindMapNode> beliefs,
                                 List<MindMapNode> evidence, String tenantId) {
        var contradictions = detectContradictions(agentId, beliefs, evidence);

        Set<String> contradictedBeliefIds = new java.util.HashSet<>();
        for (var c : contradictions) {
            var beliefNode = beliefs.stream()
                                    .filter(b -> b.id().equals(c.beliefNodeId) || b.name().equals(c.beliefText))
                                    .findFirst().orElse(null);
            if (beliefNode == null) {continue;}
            contradictedBeliefIds.add(beliefNode.id());

            double currentConfidence = beliefNode.confidence().value();
            double effectiveDecay    = config.beliefDecayPerContradiction() * c.contradictionStrength;
            double newConfidence     = Math.max(0.0, currentConfidence - effectiveDecay);
            if (newConfidence < config.beliefSupersessionThreshold()) {
                var subjectKey = beliefNode.property("subject").orElse(beliefNode.name());
                var newNodeId = mindMapStore.addNode(
                        NodeInput.of(c.revisedBelief, beliefNode.subgraphId())
                                 .withConfidence(Confidence.inferred(
                                         config.revisedBeliefInitialConfidence(), Instant.now()))
                                 .withProvenance(REVISION_PROVENANCE)
                                 .withTraits(Set.of(BELIEF_TRAIT))
                                 .withProperties(Map.of("subject", subjectKey))
                                 .withPrincipalId(PrincipalId.agent(agentId)),
                        tenantId);
                mindMapStore.supersede(beliefNode.id(), newNodeId, c.reasoning, tenantId);
            } else {
                mindMapStore.updateNode(beliefNode.id(),
                                        NodeUpdate.empty().withConfidence(
                                                new Confidence(ConfidenceOrigin.INFERRED, newConfidence, Instant.now())),
                                        tenantId);
            }
        }

        for (var beliefNode : beliefs) {
            if (contradictedBeliefIds.contains(beliefNode.id())) {continue;}
            double currentConfidence = beliefNode.confidence().value();
            if (currentConfidence >= 1.0) {continue;}
            var subjectKey = beliefNode.property("subject").orElse(beliefNode.name());
            boolean supported = evidence.stream().anyMatch(ev ->
                                                                   ev.name() != null && subjectKey != null
                                                                   && ev.name().toLowerCase(java.util.Locale.ROOT)
                                                                        .contains(subjectKey.toLowerCase(java.util.Locale.ROOT)));
            if (supported) {
                double reinforced = Math.min(currentConfidence + config.beliefReinforcementStep(), 1.0);
                mindMapStore.updateNode(beliefNode.id(),
                                        NodeUpdate.empty().withConfidence(
                                                new Confidence(ConfidenceOrigin.INFERRED, reinforced, Instant.now())),
                                        tenantId);
            }
        }

        return !contradictions.isEmpty();
    }

    record Contradiction(String beliefNodeId, String beliefText,
                         String contradictingEvidence, String reasoning,
                         double contradictionStrength, String revisedBelief) {}

    record CursorInfo(String subgraphId, String nodeId, Instant lastProcessedAt) {}


    private List<Contradiction> detectContradictions(String agentId,
            List<MindMapNode> beliefs, List<MindMapNode> evidence) {
        var beliefsText = new StringBuilder();
        for (int i = 0; i < beliefs.size(); i++) {
            var b = beliefs.get(i);
            beliefsText.append(String.format("%d. [%s] \"%s\" (confidence: %.2f)\n",
                i + 1, b.id(), b.name(), b.confidence().value()));
        }

        var evidenceText = new StringBuilder();
        for (var e : evidence) {
            var eventType = e.properties().getOrDefault("event-type", "unknown");
            evidenceText.append(String.format("- \"%s\" (event-type: %s)\n", e.name(), eventType));
        }

        var userPrompt = String.format("""
                Character: %s

                Current beliefs:
                %s
                Recent evidence:
                %s
                For each belief contradicted by this evidence, provide JSON:
                - beliefNodeId: the ID in brackets above
                - beliefText: the belief text
                - contradictingEvidence: which evidence contradicts it
                - reasoning: why it's a contradiction
                - contradictionStrength: 0.0 (barely relevant) to 1.0 (directly disproven)
                - revisedBelief: what the character should now believe (one sentence, their perspective)""",
                agentId, beliefsText, evidenceText);

        var responseText = new StringBuilder();
        ParallelLlmExecutor.execute(sessionPipeline, "consolidation", 1, SYSTEM_PROMPT, "belief-revision",
            List.of(session -> {
                session.query(userPrompt).subscribe().asStream()
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .forEach(responseText::append);
            }));

        return parseContradictions(responseText.toString());
    }

    private List<Contradiction> parseContradictions(String json) {
        json = json.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
        }
        var results = new ArrayList<Contradiction>();
        int searchFrom = 0;
        while (true) {
            int objStart = json.indexOf('{', searchFrom);
            if (objStart < 0) break;
            int objEnd = json.indexOf('}', objStart);
            if (objEnd < 0) break;
            String obj = json.substring(objStart, objEnd + 1);
            searchFrom = objEnd + 1;

            if (!obj.contains("beliefNodeId")) continue;

            var nodeId = extractField(obj, "beliefNodeId");
            var text = extractField(obj, "beliefText");
            var evidence = extractField(obj, "contradictingEvidence");
            var reasoning = extractField(obj, "reasoning");
            var strength = extractDoubleField(obj, "contradictionStrength");
            var revised = extractField(obj, "revisedBelief");

            if (nodeId != null && text != null && strength > 0) {
                results.add(new Contradiction(nodeId, text, evidence,
                    reasoning != null ? reasoning : "LLM-detected contradiction",
                    strength, revised != null ? revised : text + " (revised)"));
            }
        }
        return results;
    }

    private static String extractField(String json, String field) {
        int start = json.indexOf("\"" + field + "\"");
        if (start < 0) return null;
        int colon = json.indexOf(':', start);
        if (colon < 0) return null;
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) return null;
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
        if (start < 0) return 0.0;
        int colon = json.indexOf(':', start);
        if (colon < 0) return 0.0;
        var numStr = new StringBuilder();
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']') break;
            if (!Character.isWhitespace(c)) numStr.append(c);
        }
        try {
            return Double.parseDouble(numStr.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void saveCursor(String defaultSubgraphId, String cursorSubgraphId,
                            String cursorNodeId, Instant lastTimestamp, String tenantId) {
        String sgId = cursorSubgraphId != null ? cursorSubgraphId : defaultSubgraphId;
        if (cursorNodeId != null) {
            mindMapStore.updateNode(cursorNodeId,
                NodeUpdate.empty().withPropertiesToSet(
                    Map.of("last-processed-timestamp", lastTimestamp.toString())),
                tenantId);
        } else {
            mindMapStore.addNode(
                NodeInput.of(CURSOR_NODE_NAME, sgId)
                    .withProvenance("belief-revision-cursor")
                    .withProperties(Map.of(
                        "cognitiveKind", "cursor",
                        "last-processed-timestamp", lastTimestamp.toString())),
                tenantId);
        }
    }

    private void saveCursor(String defaultSubgraphId, String cursorSubgraphId,
                            String cursorNodeId, Instant lastTimestamp, String tenantId,
                            String cursorName) {
        String sgId = cursorSubgraphId != null ? cursorSubgraphId : defaultSubgraphId;
        if (cursorNodeId != null) {
            mindMapStore.updateNode(cursorNodeId,
                                    NodeUpdate.empty().withPropertiesToSet(
                                            Map.of("last-processed-timestamp", lastTimestamp.toString())),
                                    tenantId);
        } else {
            mindMapStore.addNode(
                    NodeInput.of(cursorName, sgId)
                             .withProvenance("belief-revision-cursor")
                             .withProperties(Map.of(
                                     "cognitiveKind", "cursor",
                                     "last-processed-timestamp", lastTimestamp.toString())),
                    tenantId);
        }
    }

}

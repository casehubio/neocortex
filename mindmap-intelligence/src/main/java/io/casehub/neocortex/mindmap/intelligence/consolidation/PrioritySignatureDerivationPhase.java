package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.session.ParallelLlmExecutor;
import io.casehub.platform.agent.session.SessionPipeline;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Derives priority signatures for drive competition resolution.
 * When multiple drives activate simultaneously, these signatures provide
 * felt-sense material about which pull to follow — grounded in the
 * character's formation memories.
 *
 * Maps to Gross's (1998) attentional deployment strategy: directing
 * attention to the most salient aspects of a situation to naturally
 * foreground the appropriate drive.
 *
 * The LLM performs implicit regulation. This phase provides richer
 * material for that implicit process in ambiguous multi-drive scenarios.
 */
@ApplicationScoped
@Priority(26)
public class PrioritySignatureDerivationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(
        PrioritySignatureDerivationPhase.class.getName());

    static final String PRIORITY_SIG_TRAIT = "PrioritySignatureDerived";

    // Multi-channel: somatic settling (Barrett 2017), attentional narrowing
    // (Easterbrook 1959), temporal shift, action tendency resolution (Frijda 1986)
    private static final String SYSTEM_PROMPT = """
        You generate priority signatures for fictional characters — what they experience
        when two competing internal pulls activate at the same time.

        Given a character's formation memories, two specific drives that may compete,
        and how those drives are described, generate what happens in the character's body
        and attention when both pulls are present and one begins to take precedence.

        This is NOT about choosing or deciding. It's about the physical settling —
        the moment when the body resolves the competition before the mind has fully
        caught up. What shifts in the chest, where attention narrows, how time changes,
        what the hands want to do.

        Ground it in the specific memories. A character whose protection drive was forged
        by watching someone get hurt settles differently from one whose protection comes
        from being the eldest sibling.

        Write in second person. 2-3 sentences. Do not name the drives or emotions.
        Do not prescribe which drive wins — describe the PROCESS of settling, not
        the outcome. The LLM will determine the outcome from context.

        Respond with JSON only: {"priority_signature": "..."}""";

    private final MindMapStore store;
    private volatile SessionPipeline sessionPipeline;

    @Inject
    public PrioritySignatureDerivationPhase(MindMapStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "priority-signature-derivation";
    }

    @Override
    public void setSessionPipeline(SessionPipeline pipeline) {
        this.sessionPipeline = pipeline;
    }


    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (sessionPipeline == null) {
            LOG.info("Priority signature derivation skipped — no SessionPipeline");
            return;
        }

        List<MindMapNode> allCognitive = store.search(
                MindMapQuery.of(tenantId, 2000).withType(SubgraphTypes.COGNITIVE));

        Map<String, List<MindMapNode>> drivesByAgent = allCognitive.stream()
                                                                   .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
                                                                   .filter(n -> n.properties().containsKey("agent-id"))
                                                                   .collect(Collectors.groupingBy(n -> n.properties().get("agent-id")));

        if (drivesByAgent.isEmpty()) {return;}

        List<MindMapNode> formativeNodes = allCognitive.stream()
                                                       .filter(n -> "formative-experience".equals(n.properties().get("cognitiveKind")))
                                                       .toList();

        Set<String> existingSignatures = allCognitive.stream()
                                                     .filter(n -> n.traits().contains(PRIORITY_SIG_TRAIT))
                                                     .map(n -> n.properties().get("agent-id") + ":" + n.properties().get("drive-pair"))
                                                     .collect(Collectors.toSet());

        Map<String, MindMapNode> descriptionsByKey = allCognitive.stream()
                                                                 .filter(n -> n.traits().contains("DriveDescriptionGenerated"))
                                                                 .collect(Collectors.toMap(
                                                                         n -> n.properties().get("agent-id") + ":" + n.properties().get("drive-type"),
                                                                         n -> n, (a, b) -> a));

        var tasks = new java.util.ArrayList<java.util.function.Consumer<AgentSession>>();
        for (var agentEntry : drivesByAgent.entrySet()) {
            String agentId = agentEntry.getKey();
            List<MindMapNode> driveNodes = agentEntry.getValue();
            if (driveNodes.size() < 2) continue;

            List<MindMapNode> agentMemories = formativeNodes.stream()
                .filter(n -> agentId.equals(n.properties().get("agent-id")))
                .toList();
            if (agentMemories.isEmpty()) continue;

            String subgraphId = findCognitiveSubgraph(tenantId, agentId);
            if (subgraphId == null) continue;

            List<String[]> pairs = generatePairs(driveNodes);

            for (String[] pair : pairs) {
                String pairKey = pair[0] + "+" + pair[1];
                if (existingSignatures.contains(agentId + ":" + pairKey)) continue;

                String descA = getDescription(descriptionsByKey, agentId, pair[0]);
                String descB = getDescription(descriptionsByKey, agentId, pair[1]);

                tasks.add(session -> {
                    long start = System.currentTimeMillis();
                    generateAndStore(session, tenantId, agentId, subgraphId, pair[0], pair[1],
                        descA, descB, agentMemories);
                    LOG.info("Priority-sig " + agentId + " " + pairKey
                        + " (" + (System.currentTimeMillis() - start) + "ms)");
                });
            }
        }

        var pipeline = this.sessionPipeline;
        if (pipeline == null) {
            LOG.warning("Priority signature derivation skipped — no SessionPipeline");
            return;
        }
        ParallelLlmExecutor.execute(pipeline, "consolidation", 6, SYSTEM_PROMPT, "priority-signature-derivation", tasks);
    }

    private List<String[]> generatePairs(List<MindMapNode> driveNodes) {
        var pairs = new ArrayList<String[]>();
        var types = driveNodes.stream()
            .map(n -> n.properties().get("drive-type"))
            .filter(java.util.Objects::nonNull)
            .sorted()
            .toList();
        for (int i = 0; i < types.size(); i++) {
            for (int j = i + 1; j < types.size(); j++) {
                pairs.add(new String[]{types.get(i), types.get(j)});
            }
        }
        return pairs;
    }

    private String getDescription(Map<String, MindMapNode> descMap, String agentId, String driveType) {
        var node = descMap.get(agentId + ":" + driveType);
        return node != null ? node.properties().getOrDefault("generated-description", "") : "";
    }

    private void generateAndStore(AgentSession session, String tenantId, String agentId, String subgraphId,
                                  String driveA, String driveB,
                                  String descA, String descB,
                                  List<MindMapNode> formationMemories) {
        try {
            String signature = callLlm(session, driveA, driveB, descA, descB, formationMemories);
            if (signature == null || signature.isBlank()) {return;}

            store.addNode(
                    NodeInput.of("priority-sig-" + driveA + "-" + driveB, subgraphId)
                             .withTraits(Set.of(PRIORITY_SIG_TRAIT))
                             .withProperties(Map.of(
                                     "agent-id", agentId,
                                     "drive-pair", driveA + "+" + driveB,
                                     "drive-a", driveA,
                                     "drive-b", driveB,
                                     "priority-signature", signature)),
                    tenantId);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    agentId + " " + driveA + "+" + driveB + ": priority signature derivation failed (non-fatal)", e);
        }
    }

    private String callLlm(AgentSession session, String driveA, String driveB,
                           String descA, String descB,
                           List<MindMapNode> formationMemories) {
        var sb = new StringBuilder();

        sb.append("Two competing pulls:\n\n");
        sb.append("Pull A (").append(driveA).append("):\n");
        if (!descA.isBlank()) {sb.append(descA).append("\n\n");}
        sb.append("Pull B (").append(driveB).append("):\n");
        if (!descB.isBlank()) {sb.append(descB).append("\n\n");}

        sb.append("Formation memories that shaped this character:\n");
        for (int i = 0; i < formationMemories.size(); i++) {
            sb.append(i + 1).append(". ").append(formationMemories.get(i).name()).append("\n");
        }

        sb.append("\nDescribe what happens in this character's body when both pulls ");
        sb.append("activate at the same time — the physical process of one beginning ");
        sb.append("to take precedence over the other. Do not say which one wins.");

        var responseText = new StringBuilder();
        session.query(sb.toString()).subscribe().asStream()
               .filter(e -> e instanceof AgentEvent.TextDelta)
               .map(e -> ((AgentEvent.TextDelta) e).text())
               .forEach(responseText::append);

        return parseSignature(responseText.toString());
    }

    private String parseSignature(String json) {
        json = json.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
        }
        int start = json.indexOf("\"priority_signature\"");
        if (start < 0) return null;
        int colon = json.indexOf(':', start);
        if (colon < 0) return null;
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) return null;
        quote++;
        var sb = new StringBuilder();
        for (int i = quote; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) { sb.append(json.charAt(++i)); }
            else if (c == '"') { break; }
            else { sb.append(c); }
        }
        return sb.toString();
    }

    private String findCognitiveSubgraph(String tenantId, String agentId) {
        return store.listSubgraphs(tenantId).stream()
            .filter(sg -> SubgraphTypes.COGNITIVE.equals(sg.type()))
            .filter(sg -> sg.id().contains(agentId) || sg.name().contains(agentId))
            .map(sg -> sg.id())
            .findFirst()
            .orElse(null);
    }
}

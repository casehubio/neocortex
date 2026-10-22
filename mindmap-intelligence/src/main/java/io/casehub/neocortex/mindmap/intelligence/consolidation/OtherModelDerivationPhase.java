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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Derives mental models of other characters from formation memories and
 * relational schemas. Produces per-relationship empathic reading content:
 * what the observer physically senses when the target is in distress.
 *
 * Based on Ong, Zaki &amp; Goodman (2019) third-person appraisal framework.
 * The Bayesian ToM engine gates WHEN this content is injected;
 * this phase produces WHAT is injected.
 */
@ApplicationScoped
@Priority(24)
public class OtherModelDerivationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(
        OtherModelDerivationPhase.class.getName());

    static final String OTHER_MODEL_TRAIT = "OtherModelDerived";

    // Multi-channel empathic sensing mirrors DriveDescriptionGenerationPhase's
// somatic/attentional/temporal/action-tendency channels (Barrett 2017, Damasio 1994,
// Easterbrook 1959, Frijda 1986), applied to sensing another person's state.
// Cognitive channels from Ong, Zaki & Goodman (2019) third-person appraisal.
    private static final String SYSTEM_PROMPT = """
                                                You generate mental models that one fictional character holds about another.
                                                
                                                Given a character's formation memories, drives, and their relationship with another character,
                                                generate how they understand and sense this other person across these dimensions:
                                                
                                                EMPATHIC SENSING — what they experience across four channels when they notice the other
                                                person is in distress:
                                                - Somatic: bodily sensations (chest, stomach, hands, jaw)
                                                - Attentional: where awareness is drawn, what they notice first
                                                - Temporal: how the moment feels (compressed, stretched, suspended)
                                                - Action tendency: what they feel pulled toward doing (not what they decide — the pull itself)
                                                Integrate all four naturally into 2-3 sentences. Do not label the channels.
                                                
                                                INFERRED VALUES — what they believe the other person cares about most deeply. What
                                                motivates them, what they protect, what they'd sacrifice for. A felt understanding,
                                                not a list. 1-2 sentences.
                                                
                                                APPRAISAL SENSITIVITIES — what kinds of situations hit the other person hardest.
                                                The specific vulnerabilities the observer has noticed — what makes them withdraw,
                                                what makes them fight, what shuts them down. 1-2 sentences.
                                                
                                                RELATIONAL UNDERSTANDING — what the bond between these two people is built on.
                                                The unspoken contract. What each gives and receives. 1-2 sentences.
                                                
                                                Write all dimensions in second person. Do not name emotions or drives explicitly.
                                                Do not prescribe action or interpretation. Ground everything in the specific
                                                memories and relationship provided.
                                                
                                                Respond with JSON only:
                                                {
                                                  "empathic_sensing": "...",
                                                  "inferred_values": "...",
                                                  "appraisal_sensitivities": "...",
                                                  "relational_understanding": "..."
                                                }""";

    private final MindMapStore store;
    private volatile SessionPipeline sessionPipeline;

    @Inject
    public OtherModelDerivationPhase(MindMapStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "other-model-derivation";
    }

    @Override
    public void setSessionPipeline(SessionPipeline pipeline) {
        this.sessionPipeline = pipeline;
    }


    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (sessionPipeline == null) {
            LOG.info("Other-model derivation skipped — no SessionPipeline");
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

        List<MindMapNode> relationalNodes = allCognitive.stream()
                                                        .filter(n -> "relational-schema".equals(n.properties().get("cognitiveKind")))
                                                        .toList();

        Set<String> existingModels = allCognitive.stream()
                                                 .filter(n -> n.traits().contains(OTHER_MODEL_TRAIT))
                                                 .map(n -> n.properties().get("agent-id") + "→" + n.properties().get("target-id"))
                                                 .collect(Collectors.toSet());

        var tasks = new java.util.ArrayList<java.util.function.Consumer<AgentSession>>();
        for (var agentEntry : drivesByAgent.entrySet()) {
            String agentId = agentEntry.getKey();

            List<MindMapNode> agentMemories = formativeNodes.stream()
                .filter(n -> agentId.equals(n.properties().get("agent-id")))
                .toList();
            if (agentMemories.isEmpty()) continue;

            List<MindMapNode> agentRelations = relationalNodes.stream()
                .filter(n -> agentId.equals(n.properties().get("agent-id")))
                .toList();

            Set<String> targetIds = agentRelations.stream()
                .map(n -> n.properties().get("target-id"))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

            for (String otherAgentId : drivesByAgent.keySet()) {
                if (otherAgentId.equals(agentId)) continue;
                targetIds.add(otherAgentId);
            }

            String subgraphId = findCognitiveSubgraph(tenantId, agentId);
            if (subgraphId == null) continue;

            List<MindMapNode> driveNodes = agentEntry.getValue();
            for (String targetId : targetIds) {
                String key = agentId + "→" + targetId;
                if (existingModels.contains(key)) continue;

                List<MindMapNode> relevantRelations = agentRelations.stream()
                    .filter(n -> targetId.equals(n.properties().get("target-id")))
                    .toList();

                tasks.add(session -> {
                    long start = System.currentTimeMillis();
                    generateAndStore(session, tenantId, agentId, targetId, subgraphId,
                        agentMemories, driveNodes, relevantRelations);
                    LOG.info("Other-model " + agentId + "→" + targetId
                        + " (" + (System.currentTimeMillis() - start) + "ms)");
                });
            }
        }

        var pipeline = this.sessionPipeline;
        if (pipeline == null) {
            LOG.warning("Other-model derivation skipped — no SessionPipeline");
            return;
        }
        ParallelLlmExecutor.execute(pipeline, "consolidation", 6, SYSTEM_PROMPT, "other-model-derivation", tasks);
    }

    private void generateAndStore(AgentSession session, String tenantId, String agentId, String targetId,
                                  String subgraphId,
                                  List<MindMapNode> formationMemories,
                                  List<MindMapNode> driveNodes,
                                  List<MindMapNode> relationalSchemas) {
        try {
            Map<String, String> model = callLlm(session, formationMemories, driveNodes, relationalSchemas);
            if (model == null || model.isEmpty()) {return;}

            var props = new java.util.HashMap<String, String>();
            props.put("agent-id", agentId);
            props.put("target-id", targetId);
            model.forEach((k, v) -> {if (v != null && !v.isBlank()) {props.put(k, v);}});

            store.addNode(
                    NodeInput.of("other-model-" + agentId + "-" + targetId, subgraphId)
                             .withTraits(Set.of(OTHER_MODEL_TRAIT))
                             .withProperties(props),
                    tenantId);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    agentId + "→" + targetId + ": other-model derivation failed (non-fatal)", e);
        }
    }

    private Map<String, String> callLlm(AgentSession session, List<MindMapNode> formationMemories,
                                        List<MindMapNode> driveNodes,
                                        List<MindMapNode> relationalSchemas) {
        var sb = new StringBuilder();

        sb.append("Character's drives:\n");
        for (var drive : driveNodes) {
            sb.append("- ").append(drive.properties().get("drive-type"))
              .append(" (intensity ").append(drive.properties().get("intensity")).append(")\n");
        }

        sb.append("\nFormation memories that shaped this character:\n");
        for (int i = 0; i < formationMemories.size(); i++) {
            sb.append(i + 1).append(". ").append(formationMemories.get(i).name()).append("\n");
        }

        if (!relationalSchemas.isEmpty()) {
            sb.append("\nRelationship with the other person:\n");
            for (var schema : relationalSchemas) {
                var desc = schema.properties().get("schema");
                sb.append("- ").append(desc != null ? desc : schema.name()).append("\n");
            }
        }

        sb.append("\nGenerate the four-dimension mental model for how this character ");
        sb.append("understands and senses this other person.");

        var responseText = new StringBuilder();
        session.query(sb.toString()).subscribe().asStream()
               .filter(e -> e instanceof AgentEvent.TextDelta)
               .map(e -> ((AgentEvent.TextDelta) e).text())
               .forEach(responseText::append);

        return parseModel(responseText.toString());
    }

    private Map<String, String> parseModel(String json) {
        json = json.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
        }
        var result = new java.util.HashMap<String, String>();
        for (String key : List.of("empathic_sensing", "inferred_values", "appraisal_sensitivities", "relational_understanding")) {
            String dashed = key.replace('_', '-');
            int    start  = json.indexOf("\"" + key + "\"");
            if (start < 0) {continue;}
            int colon = json.indexOf(':', start);
            if (colon < 0) {continue;}
            int quote = json.indexOf('"', colon + 1);
            if (quote < 0) {continue;}
            quote++;
            var sb = new StringBuilder();
            for (int i = quote; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {sb.append(json.charAt(++i));} else if (c == '"') {
                    break;
                } else {sb.append(c);}
            }
            String value = sb.toString().strip();
            if (!value.isEmpty()) {
                result.put(dashed, value);
            }
        }
        return result;
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

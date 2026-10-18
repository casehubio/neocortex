package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

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

    private static final String SYSTEM_PROMPT = """
        You generate empathic sensing descriptions for fictional characters.

        Given a character's formation memories and their relationship with another character,
        generate what they would physically sense when they notice the other person is in distress.

        Not what they think, conclude, or decide to do — what they FEEL in their body when they
        pick up that something is wrong with someone they have a bond with. The tightening in the
        chest, the shift in attention, the pull toward or away from.

        Ground it in the specific relationship and memories. A protector senses differently from
        a schemer. A character shaped by abandonment notices withdrawal differently from one
        shaped by competition.

        Write in second person. 2-3 sentences. Do not name emotions or drives explicitly.
        Do not prescribe action or interpretation.

        Respond with JSON only: {"empathic_reading": "..."}""";

    private final MindMapStore store;
    private final @Nullable AgentProvider agentProvider;

    @Inject
    public OtherModelDerivationPhase(MindMapStore store,
                                      Instance<AgentProvider> agentProvider) {
        this(store, agentProvider.isResolvable() ? agentProvider.get() : null);
    }

    OtherModelDerivationPhase(MindMapStore store,
                               @Nullable AgentProvider agentProvider) {
        this.store = store;
        this.agentProvider = agentProvider;
    }

    @Override
    public String name() {
        return "other-model-derivation";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (agentProvider == null) {
            LOG.info("Other-model derivation skipped — no AgentProvider");
            return;
        }

        List<MindMapNode> allCognitive = store.search(
            MindMapQuery.of(tenantId, 2000).withType(SubgraphTypes.COGNITIVE));

        Map<String, List<MindMapNode>> drivesByAgent = allCognitive.stream()
            .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
            .filter(n -> n.properties().containsKey("agent-id"))
            .collect(Collectors.groupingBy(n -> n.properties().get("agent-id")));

        if (drivesByAgent.isEmpty()) return;

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

            for (String targetId : targetIds) {
                String key = agentId + "→" + targetId;
                if (existingModels.contains(key)) continue;

                List<MindMapNode> relevantRelations = agentRelations.stream()
                    .filter(n -> targetId.equals(n.properties().get("target-id")))
                    .toList();

                long start = System.currentTimeMillis();
                generateAndStore(tenantId, agentId, targetId, subgraphId,
                    agentMemories, agentEntry.getValue(), relevantRelations);
                LOG.info("Other-model " + agentId + "→" + targetId
                    + " (" + (System.currentTimeMillis() - start) + "ms)");
            }
        }
    }

    private void generateAndStore(String tenantId, String agentId, String targetId,
                                   String subgraphId,
                                   List<MindMapNode> formationMemories,
                                   List<MindMapNode> driveNodes,
                                   List<MindMapNode> relationalSchemas) {
        try {
            String reading = callLlm(formationMemories, driveNodes, relationalSchemas);
            if (reading == null || reading.isBlank()) return;

            store.addNode(
                NodeInput.of("other-model-" + agentId + "-" + targetId, subgraphId)
                    .withTraits(Set.of(OTHER_MODEL_TRAIT))
                    .withProperties(Map.of(
                        "agent-id", agentId,
                        "target-id", targetId,
                        "empathic-reading", reading)),
                tenantId);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                agentId + "→" + targetId + ": other-model derivation failed (non-fatal)", e);
        }
    }

    private String callLlm(List<MindMapNode> formationMemories,
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
                sb.append("- ").append(schema.name()).append("\n");
            }
        }

        sb.append("\nDescribe what this character physically senses when they pick up ");
        sb.append("that the other person is in distress. Ground it in the memories and relationship above.");

        var sessionConfig = AgentSessionConfig.of(SYSTEM_PROMPT, sb.toString());
        var responseText = new StringBuilder();
        agentProvider.invoke(sessionConfig).subscribe().asStream()
            .filter(e -> e instanceof AgentEvent.TextDelta)
            .map(e -> ((AgentEvent.TextDelta) e).text())
            .forEach(responseText::append);

        return parseReading(responseText.toString());
    }

    private String parseReading(String json) {
        json = json.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
        }
        int start = json.indexOf("\"empathic_reading\"");
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

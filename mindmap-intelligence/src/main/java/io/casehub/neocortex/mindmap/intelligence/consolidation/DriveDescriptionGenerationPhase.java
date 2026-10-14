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

import io.casehub.neocortex.mindmap.NodeUpdate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Generates somatic drive descriptions via LLM from formation memories.
 * Runs after DriveProfileDerivationPhase — replaces hand-authored descriptions
 * with character-specific, intensity-appropriate somatic text.
 *
 * See research doc §10.10.8, decision D13.
 */
@ApplicationScoped
@Priority(21)
public class DriveDescriptionGenerationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(
        DriveDescriptionGenerationPhase.class.getName());

    static final String DESCRIPTION_TRAIT = "DriveDescriptionGenerated";

    private static final String SYSTEM_PROMPT = """
            You generate somatic descriptions of internal drives for fictional characters.
            Each description captures what a character physically feels when a particular \
            motivational pull activates — the bodily sensations, not actions or behaviors.
            Write in second person. Describe only somatic/bodily sensations.
            Do not name the drive or pull. Do not explain what it is.
            Do not include behavioral instructions or action descriptions.
            Keep each description to 2-3 sentences.
            Respond with JSON only: {"description": "..."}""";

    private final MindMapStore store;
    private final @Nullable AgentProvider agentProvider;

    @Inject
    public DriveDescriptionGenerationPhase(MindMapStore store,
                                            Instance<AgentProvider> agentProvider) {
        this(store, agentProvider.isResolvable() ? agentProvider.get() : null);
    }

    DriveDescriptionGenerationPhase(MindMapStore store,
                                    @Nullable AgentProvider agentProvider) {
        this.store = store;
        this.agentProvider = agentProvider;
    }

    @Override
    public String name() {
        return "drive-description-generation";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (agentProvider == null) {
            LOG.info("Drive description generation skipped — no AgentProvider");
            return;
        }

        List<MindMapNode> allCognitive = store.search(
            MindMapQuery.of(tenantId, 2000).withType(SubgraphTypes.COGNITIVE));

        Map<String, List<MindMapNode>> drivesByAgent = allCognitive.stream()
            .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
            .filter(n -> n.properties().containsKey("agent-id"))
            .collect(Collectors.groupingBy(n -> n.properties().get("agent-id")));

        if (drivesByAgent.isEmpty()) {
            LOG.info("Drive description generation skipped — no drive-intensity nodes found in " + allCognitive.size() + " cognitive nodes");
            return;
        }

        List<MindMapNode> formativeNodes = allCognitive.stream()
            .filter(n -> "formative-experience".equals(n.properties().get("cognitiveKind")))
            .toList();

        var kindCounts = allCognitive.stream()
            .map(n -> n.properties().getOrDefault("cognitiveKind", "none"))
            .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
        LOG.info("Drive description generation: " + drivesByAgent.size() + " agents, "
            + drivesByAgent.values().stream().mapToInt(List::size).sum() + " drives, "
            + formativeNodes.size() + " formative memories. Node kinds: " + kindCounts);

        for (var agentEntry : drivesByAgent.entrySet()) {
            String agentId = agentEntry.getKey();
            List<MindMapNode> driveNodes = agentEntry.getValue();

            List<MindMapNode> agentMemories = formativeNodes.stream()
                .filter(n -> agentId.equals(n.properties().get("agent-id")))
                .toList();

            if (agentMemories.isEmpty()) continue;

            removeExistingDescriptions(tenantId, agentId, allCognitive);

            String subgraphId = findCognitiveSubgraph(tenantId, agentId);
            if (subgraphId == null) continue;

            for (MindMapNode driveNode : driveNodes) {
                String driveType = driveNode.properties().get("drive-type");
                if (driveType == null) continue;

                List<MindMapNode> servingMemories = agentMemories.stream()
                    .filter(m -> memoryServesDrive(m, driveType))
                    .toList();

                String intensity = driveNode.properties().getOrDefault("intensity", "0.5");

                generateAndStore(tenantId, agentId, subgraphId,
                    driveType, intensity, servingMemories);
            }

            generateDiscriminationText(tenantId, agentId, allCognitive);
        }
    }

    private void generateAndStore(String tenantId, String agentId, String subgraphId,
                                   String driveType, String intensity,
                                   List<MindMapNode> servingMemories) {
        try {
            String description = callLlm(driveType, intensity, servingMemories);
            if (description == null || description.isBlank()) return;

            store.addNode(
                NodeInput.of("drive-description-" + driveType, subgraphId)
                    .withTraits(Set.of(DESCRIPTION_TRAIT))
                    .withProperties(Map.of(
                        "agent-id", agentId,
                        "drive-type", driveType,
                        "drive-intensity", intensity,
                        "generated-description", description,
                        "source-memory-count", String.valueOf(servingMemories.size()))),
                tenantId);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                agentId + "/" + driveType + ": description generation failed (non-fatal)", e);
        }
    }

    private String callLlm(String driveType, String intensity,
                            List<MindMapNode> servingMemories) {
        String userPrompt;
        if (servingMemories.isEmpty()) {
            userPrompt = String.format("""
                Drive type: %s
                Intensity: %s (scale 0.0 to 1.0)

                Describe what a person feels in their body when this type of pull activates \
                at this intensity — without naming the pull. Second person, somatic only, \
                no behavioral instructions.""",
                driveType, intensity);
        } else {
            var memoriesText = new StringBuilder();
            for (int i = 0; i < servingMemories.size(); i++) {
                memoriesText.append(String.format("%d. %s\n", i + 1, servingMemories.get(i).name()));
            }
            userPrompt = String.format("""
                Drive type: %s
                Intensity: %s (scale 0.0 to 1.0)

                Formation memories that shaped this pull:
                %s
                Given these formation memories, describe what this person feels in their body \
                when this pull activates — without naming the pull. Second person, somatic only, \
                no behavioral instructions.""",
                driveType, intensity, memoriesText);
        }

        var sessionConfig = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt);
        var responseText = new StringBuilder();
        agentProvider.invoke(sessionConfig).subscribe().asStream()
            .filter(e -> e instanceof AgentEvent.TextDelta)
            .map(e -> ((AgentEvent.TextDelta) e).text())
            .forEach(responseText::append);

        return parseDescription(responseText.toString());
    }

    private static final String DISCRIMINATION_SYSTEM = """
            You generate somatic discrimination criteria for competing motivational drives.
            Given formation memories for two drives that often co-activate, describe how \
            the two pulls FEEL DIFFERENT in the body.
            Use "one pull" and "the other" — do not name the drives.
            Frame in terms of bodily sensations and the quality of the act, not memory content.
            End with a noticing instruction: "Notice which one [somatic criterion]."
            Do NOT mention what's broken, what needs solving, or situational content.
            Keep to 3-4 sentences.
            Respond with JSON only: {"discrimination": "..."}""";

    private void generateDiscriminationText(String tenantId, String agentId,
                                             List<MindMapNode> allCognitive) {
        var competitionNodes = allCognitive.stream()
            .filter(n -> n.traits().contains(DriveProfileDerivationPhase.COMPETITION_TRAIT))
            .filter(n -> agentId.equals(n.properties().get("agent-id")))
            .toList();

        for (var node : competitionNodes) {
            String memoriesA = node.properties().getOrDefault("anchor-memories-a", "");
            String memoriesB = node.properties().getOrDefault("anchor-memories-b", "");
            if (memoriesA.isBlank() && memoriesB.isBlank()) continue;

            try {
                String discrimination = callDiscriminationLlm(
                    memoriesA.replace("|", "\n"), memoriesB.replace("|", "\n"));
                if (discrimination != null && !discrimination.isBlank()) {
                    store.updateNode(node.id(),
                        NodeUpdate.empty().withPropertiesToSet(
                            Map.of("differentiation-text", discrimination)),
                        tenantId);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, agentId + ": discrimination generation failed (non-fatal)", e);
            }
        }
    }

    private String callDiscriminationLlm(String memoriesA, String memoriesB) {
        var userPrompt = String.format("""
                Two pulls often activate together in the same moment.

                Pull A was shaped by these experiences:
                %s

                Pull B was shaped by these experiences:
                %s

                Generate a somatic discrimination criterion — how these two pulls \
                FEEL DIFFERENT in the body. Frame as "How they differ:" followed by \
                the criterion. The criterion must work regardless of the current situation.""",
                memoriesA, memoriesB);

        var sessionConfig = AgentSessionConfig.of(DISCRIMINATION_SYSTEM, userPrompt);
        var responseText = new StringBuilder();
        agentProvider.invoke(sessionConfig).subscribe().asStream()
            .filter(e -> e instanceof AgentEvent.TextDelta)
            .map(e -> ((AgentEvent.TextDelta) e).text())
            .forEach(responseText::append);

        return parseField(responseText.toString(), "discrimination");
    }

    private String parseField(String json, String fieldName) {
        json = json.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
        }
        int start = json.indexOf("\"" + fieldName + "\"");
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

    private String parseDescription(String json) {
        return parseField(json, "description");
    }

    private boolean memoryServesDrive(MindMapNode memoryNode, String driveType) {
        String situationTypes = memoryNode.properties().get("situation-types");
        if (situationTypes == null) return false;
        return Arrays.asList(situationTypes.split(",")).contains(driveType);
    }

    private void removeExistingDescriptions(String tenantId, String agentId,
                                             List<MindMapNode> allCognitive) {
        allCognitive.stream()
            .filter(n -> n.traits().contains(DESCRIPTION_TRAIT))
            .filter(n -> agentId.equals(n.properties().get("agent-id")))
            .forEach(n -> store.eraseNode(n.id(), tenantId));
    }

    private String findCognitiveSubgraph(String tenantId, String agentId) {
        return store.listSubgraphs(tenantId).stream()
            .filter(sg -> SubgraphTypes.COGNITIVE.equals(sg.type()))
            .filter(sg -> sg.id().contains(agentId) || sg.name().contains(agentId))
            .map(sg -> sg.id())
            .findFirst()
            .orElse(store.listSubgraphs(tenantId).stream()
                .filter(sg -> SubgraphTypes.COGNITIVE.equals(sg.type()))
                .map(sg -> sg.id())
                .findFirst()
                .orElse(null));
    }
}

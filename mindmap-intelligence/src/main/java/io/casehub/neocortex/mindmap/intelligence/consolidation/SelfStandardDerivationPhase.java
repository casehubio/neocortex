package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Generates self-standard texts via LLM from formation memories.
 * Runs after DriveDescriptionGenerationPhase — produces per-drive
 * self-evaluation signatures capturing the felt sense of meeting
 * or falling short of internalized standards.
 *
 * Based on Tracy &amp; Robins (2004) self-conscious emotions model.
 */
@ApplicationScoped
@Priority(22)
public class SelfStandardDerivationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(
        SelfStandardDerivationPhase.class.getName());

    static final String SELF_STANDARD_TRAIT = "SelfStandardDerived";

    private static final String SYSTEM_PROMPT = """
                                                You generate self-evaluation signatures for fictional characters' internal drives.

                                                Given formation memories and a specific drive, generate a self-standard description that captures:
                                                - The felt sense of meeting this character's internalized standard (somatic warmth, clarity, physical alignment)
                                                - The felt sense of falling short (somatic discomfort, weight, restlessness)

                                                These sensations must be SPECIFIC to this character's memories — not generic pride/guilt.
                                                They should feel like embodied self-knowledge: the character has felt these sensations before and recognizes them.

                                                Write in second person. 2-3 sentences. Do not name the drive or emotions explicitly.
                                                Weave success and failure sensations naturally — they are two sides of the same self-standard.

                                                Respond with JSON only: {"self_standard": "..."}""";

    private final MindMapStore store;
    private final @Nullable AgentProvider agentProvider;

    @Inject
    public SelfStandardDerivationPhase(MindMapStore store,
                                        Instance<AgentProvider> agentProvider) {
        this(store, agentProvider.isResolvable() ? agentProvider.get() : null);
    }

    SelfStandardDerivationPhase(MindMapStore store,
                                 @Nullable AgentProvider agentProvider) {
        this.store = store;
        this.agentProvider = agentProvider;
    }

    @Override
    public String name() {
        return "self-standard-derivation";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (agentProvider == null) {
            LOG.info("Self-standard derivation skipped — no AgentProvider");
            return;
        }

        List<MindMapNode> allCognitive = store.search(
                MindMapQuery.of(tenantId, 2000).withType(SubgraphTypes.COGNITIVE));

        Map<String, List<MindMapNode>> drivesByAgent = allCognitive.stream()
                                                                   .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
                                                                   .filter(n -> n.properties().containsKey("agent-id"))
                                                                   .collect(Collectors.groupingBy(n -> n.properties().get("agent-id")));

        if (drivesByAgent.isEmpty()) {
            LOG.info("Self-standard derivation skipped — no drive-intensity nodes found in " + allCognitive.size() + " cognitive nodes");
            return;
        }

        List<MindMapNode> formativeNodes = allCognitive.stream()
                                                       .filter(n -> "formative-experience".equals(n.properties().get("cognitiveKind")))
                                                       .toList();

        var kindCounts = allCognitive.stream()
                                     .map(n -> n.properties().getOrDefault("cognitiveKind", "none"))
                                     .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
        LOG.info("Self-standard derivation: " + drivesByAgent.size() + " agents, "
                 + drivesByAgent.values().stream().mapToInt(List::size).sum() + " drives, "
                 + formativeNodes.size() + " formative memories. Node kinds: " + kindCounts);

        try (AgentSession session = agentProvider.openSession(AgentSessionInit.of(SYSTEM_PROMPT))) {
            int agentIndex  = 0;
            int totalAgents = drivesByAgent.size();
            for (var agentEntry : drivesByAgent.entrySet()) {
                agentIndex++;
                String            agentId    = agentEntry.getKey();
                List<MindMapNode> driveNodes = agentEntry.getValue();

                Set<String> derivedDriveTypes = allCognitive.stream()
                                                            .filter(n -> n.traits().contains(SELF_STANDARD_TRAIT))
                                                            .filter(n -> agentId.equals(n.properties().get("agent-id")))
                                                            .map(n -> n.properties().get("drive-type"))
                                                            .filter(java.util.Objects::nonNull)
                                                            .collect(Collectors.toSet());

                Set<String> currentDriveTypes = driveNodes.stream()
                                                          .map(n -> n.properties().get("drive-type"))
                                                          .filter(java.util.Objects::nonNull)
                                                          .collect(Collectors.toSet());

                if (derivedDriveTypes.containsAll(currentDriveTypes)) {
                    LOG.info("Self-standard derivation [" + agentIndex + "/" + totalAgents + "]: "
                             + agentId + " skipped — self-standards already exist");
                    continue;
                }

                List<MindMapNode> agentMemories = formativeNodes.stream()
                                                                .filter(n -> agentId.equals(n.properties().get("agent-id")))
                                                                .toList();

                if (agentMemories.isEmpty()) {continue;}

                LOG.info("Self-standard derivation [" + agentIndex + "/" + totalAgents + "]: "
                         + agentId + " (" + driveNodes.size() + " drives, "
                         + agentMemories.size() + " memories)");

                String subgraphId = findCognitiveSubgraph(tenantId, agentId);
                if (subgraphId == null) {continue;}

                for (MindMapNode driveNode : driveNodes) {
                    String driveType = driveNode.properties().get("drive-type");
                    if (driveType == null || derivedDriveTypes.contains(driveType)) {continue;}

                    List<MindMapNode> servingMemories = agentMemories.stream()
                                                                     .filter(m -> memoryServesDrive(m, driveType))
                                                                     .toList();

                    String intensity = driveNode.properties().getOrDefault("intensity", "0.5");

                    long start = System.currentTimeMillis();
                    generateAndStore(session, tenantId, agentId, subgraphId,
                                     driveType, intensity, servingMemories);
                    LOG.info("  " + agentId + "/" + driveType + " (" + servingMemories.size()
                             + " memories, " + (System.currentTimeMillis() - start) + "ms)");
                }
            }
        }
    }

    private void generateAndStore(AgentSession session, String tenantId, String agentId, String subgraphId,
                                  String driveType, String intensity,
                                  List<MindMapNode> servingMemories) {
        try {
            String selfStandard = callLlm(session, driveType, intensity, servingMemories);
            if (selfStandard == null || selfStandard.isBlank()) {return;}

            store.addNode(
                    NodeInput.of("self-standard-" + driveType, subgraphId)
                             .withTraits(Set.of(SELF_STANDARD_TRAIT))
                             .withProperties(Map.of(
                                     "agent-id", agentId,
                                     "drive-type", driveType,
                                     "drive-intensity", intensity,
                                     "self-standard", selfStandard,
                                     "source-memory-count", String.valueOf(servingMemories.size()))),
                    tenantId);
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    agentId + "/" + driveType + ": self-standard derivation failed (non-fatal)", e);
        }
    }

    private String callLlm(AgentSession session, String driveType, String intensity,
                           List<MindMapNode> servingMemories) {
        String userPrompt;
        if (servingMemories.isEmpty()) {
            userPrompt = String.format("""
                                       Drive type: %s
                                       Intensity: %s (scale 0.0 to 1.0)
                                       
                                       Describe what self-evaluation feels like for a person regarding this \
                                       type of pull at this intensity — the quiet certainty when they meet \
                                       their own standard, and the weight when they don't. Second person, \
                                       do not name the drive or emotions explicitly.""",
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
                                       Based on these memories, describe what self-evaluation feels like for this character \
                                       regarding this pull — the quiet certainty when they meet their own standard, and the \
                                       weight when they don't. Ground in the specific memories above.""",
                                       driveType, intensity, memoriesText);
        }

        var responseText = new StringBuilder();
        session.query(userPrompt).subscribe().asStream()
               .filter(e -> e instanceof AgentEvent.TextDelta)
               .map(e -> ((AgentEvent.TextDelta) e).text())
               .forEach(responseText::append);
        session.clear();

        return parseSelfStandard(responseText.toString());
    }

    private String parseSelfStandard(String json) {
        json = json.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
        }
        int start = json.indexOf("\"self_standard\"");
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

    private boolean memoryServesDrive(MindMapNode memoryNode, String driveType) {
        String situationTypes = memoryNode.properties().get("situation-types");
        if (situationTypes == null) return false;
        return Arrays.asList(situationTypes.split(",")).contains(driveType);
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

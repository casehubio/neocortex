package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.session.ParallelLlmExecutor;
import io.casehub.platform.agent.session.SessionPipeline;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
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

    // Barrett (2017) constructionist 4-channel framework: somatic (Damasio 1994), attentional
    // (Easterbrook 1959), temporal (Barrett prediction), action tendency (Frijda 1986)
    private static final String SYSTEM_PROMPT = """
                                                You derive character-specific drive profiles from personality and formation memories.
                                                For each drive, describe how it TYPICALLY manifests in THIS person's body — shaped \
                                                by their unique history, personality scores, and attachment style.
                                                
                                                Personality scores are body calibration, not opinions: high Callousness means the \
                                                sympathy region is quiet; high Suspiciousness means shoulders square before worry \
                                                registers. Attachment is the somatic ground state: avoidant bodies find calm in \
                                                control, secure bodies settle into closeness, anxious bodies amplify every signal.
                                                
                                                Cover four channels woven together (not as a list):
                                                - Somatic: where does this drive live in THIS person? What chronic tensions, warmths, \
                                                numbnesses has their history created?
                                                - Attentional: what does this drive make them scan for, fixate on, miss?
                                                - Temporal: does this drive make moments feel urgent, patient, compressed, stretched?
                                                - Action tendency: what does their body prepare to do when this activates?
                                                
                                                Ground each channel in specific formation memories where possible.
                                                Write in second person. Do not name the drive. 2-3 sentences per drive.
                                                Respond with JSON only: {"description": "..."}""";

    private final MindMapStore store;
    private volatile SessionPipeline sessionPipeline;

    @Inject
    public DriveDescriptionGenerationPhase(MindMapStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "drive-description-generation";
    }

    @Override
    public void setSessionPipeline(SessionPipeline pipeline) {
        this.sessionPipeline = pipeline;
    }


    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (sessionPipeline == null) {
            LOG.info("Drive description generation skipped — no SessionPipeline");
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

        var descTasks = new java.util.ArrayList<java.util.function.Consumer<AgentSession>>();
        var discTasks = new java.util.ArrayList<java.util.function.Consumer<AgentSession>>();
        int agentIndex = 0;
        int totalAgents = drivesByAgent.size();
        for (var agentEntry : drivesByAgent.entrySet()) {
            agentIndex++;
            String agentId = agentEntry.getKey();
            List<MindMapNode> driveNodes = agentEntry.getValue();

            Set<String> describedDriveTypes = allCognitive.stream()
                .filter(n -> n.traits().contains(DESCRIPTION_TRAIT))
                .filter(n -> agentId.equals(n.properties().get("agent-id")))
                .map(n -> n.properties().get("drive-type"))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

            Set<String> currentDriveTypes = driveNodes.stream()
                .map(n -> n.properties().get("drive-type"))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

            if (describedDriveTypes.containsAll(currentDriveTypes)) {
                LOG.info("Description generation [" + agentIndex + "/" + totalAgents + "]: "
                    + agentId + " skipped — descriptions already exist");
                continue;
            }

            List<MindMapNode> agentMemories = formativeNodes.stream()
                .filter(n -> agentId.equals(n.properties().get("agent-id")))
                .toList();

            List<MindMapNode> personalityNodes = allCognitive.stream()
                .filter(n -> "personality-facet".equals(n.properties().get("cognitiveKind")))
                .filter(n -> agentId.equals(n.properties().get("agent-id")))
                .toList();

            MindMapNode attachmentNode = allCognitive.stream()
                .filter(n -> "attachment-style".equals(n.properties().get("cognitiveKind")))
                .filter(n -> agentId.equals(n.properties().get("agent-id")))
                .findFirst().orElse(null);

            if (agentMemories.isEmpty() && personalityNodes.isEmpty()) continue;

            LOG.info("Description generation [" + agentIndex + "/" + totalAgents + "]: "
                + agentId + " (" + driveNodes.size() + " drives, "
                + agentMemories.size() + " memories)");

            String subgraphId = findCognitiveSubgraph(tenantId, agentId);
            if (subgraphId == null) continue;

            for (MindMapNode driveNode : driveNodes) {
                String driveType = driveNode.properties().get("drive-type");
                if (driveType == null || describedDriveTypes.contains(driveType)) continue;

                List<MindMapNode> servingMemories = agentMemories.stream()
                    .filter(m -> memoryServesDrive(m, driveType))
                    .toList();

                String intensity = driveNode.properties().getOrDefault("intensity", "0.5");

                descTasks.add(session -> {
                    long start = System.currentTimeMillis();
                    generateAndStore(session, tenantId, agentId, subgraphId,
                        driveType, intensity, servingMemories, personalityNodes, attachmentNode);
                    LOG.info("  " + agentId + "/" + driveType + " (" + servingMemories.size()
                        + " memories, " + personalityNodes.size() + " facets, "
                        + (System.currentTimeMillis() - start) + "ms)");
                });
            }

            discTasks.add(session -> {
                long discStart = System.currentTimeMillis();
                generateDiscriminationText(session, tenantId, agentId, allCognitive);
                LOG.info("  " + agentId + " discrimination: "
                    + (System.currentTimeMillis() - discStart) + "ms");
            });
        }

        var pipeline = this.sessionPipeline;
        if (pipeline == null) {
            LOG.warning("Drive description generation skipped — no SessionPipeline");
            return;
        }
        ParallelLlmExecutor.execute(pipeline, "consolidation", 6, SYSTEM_PROMPT, "drive-description", descTasks);
        ParallelLlmExecutor.execute(pipeline, "consolidation", 6, DISCRIMINATION_SYSTEM, "drive-discrimination", discTasks);
    }

    private void generateAndStore(AgentSession session, String tenantId, String agentId, String subgraphId,
                                  String driveType, String intensity,
                                  List<MindMapNode> servingMemories,
                                  List<MindMapNode> personalityNodes, MindMapNode attachmentNode) {
        try {
            String description = callLlm(session, driveType, intensity, servingMemories,
                                          personalityNodes, attachmentNode);
            if (description == null || description.isBlank()) {return;}

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

    private String callLlm(AgentSession session, String driveType, String intensity,
                           List<MindMapNode> servingMemories,
                           List<MindMapNode> personalityNodes, MindMapNode attachmentNode) {
        var promptBuilder = new StringBuilder();
        promptBuilder.append("Drive type: ").append(driveType).append("\n");
        promptBuilder.append("Intensity: ").append(intensity).append(" (scale 0.0 to 1.0)\n\n");

        if (!personalityNodes.isEmpty()) {
            promptBuilder.append("Personality profile:\n");
            for (var node : personalityNodes) {
                promptBuilder.append("  ").append(node.properties().get("facet"))
                             .append(": ").append(node.properties().get("score")).append("/100\n");
            }
            promptBuilder.append("\n");
        }

        if (attachmentNode != null) {
            promptBuilder.append("Attachment: ").append(attachmentNode.properties().get("style"))
                         .append(" (anxiety=").append(attachmentNode.properties().get("anxiety"))
                         .append(", avoidance=").append(attachmentNode.properties().get("avoidance")).append(")\n\n");
        }

        if (!servingMemories.isEmpty()) {
            promptBuilder.append("Formation memories that shaped this pull:\n");
            for (int i = 0; i < servingMemories.size(); i++) {
                promptBuilder.append(i + 1).append(". ").append(servingMemories.get(i).name()).append("\n");
            }
            promptBuilder.append("\n");
        }

        promptBuilder.append("Given this character's personality and formation memories, describe how this pull ")
                     .append("TYPICALLY manifests in their body — without naming the pull. ")
                     .append("Weave all four channels together. Second person, no behavioral instructions.");

        String userPrompt = promptBuilder.toString();

        var responseText = new StringBuilder();
        session.query(userPrompt).subscribe().asStream()
               .filter(e -> e instanceof AgentEvent.TextDelta)
               .map(e -> ((AgentEvent.TextDelta) e).text())
               .forEach(responseText::append);

        return parseDescription(responseText.toString());
    }

    // Multi-channel discrimination — same expression model as SYSTEM_PROMPT
    private static final String DISCRIMINATION_SYSTEM = """
                                                        You generate discrimination criteria for competing motivational drives.
                                                        Given formation memories for two drives that often co-activate, describe how \
                                                        the two pulls FEEL DIFFERENT — across multiple channels:
                                                        - Somatic: different bodily sensations (location, quality, movement)
                                                        - Attentional: different things that capture attention or awareness
                                                        - Temporal: different qualities of the moment (pace, urgency, suspension)
                                                        - Action tendency: different things the person feels pulled to do
                                                        Use "one pull" and "the other" — do not name the drives.
                                                        Frame in terms of what the person experiences, not memory content.
                                                        End with a noticing instruction: "Notice which one [multi-channel criterion]."
                                                        Do NOT mention what's broken, what needs solving, or situational content.
                                                        Keep to 3-4 sentences.
                                                        Respond with JSON only: {"discrimination": "..."}""";

    private void generateDiscriminationText(AgentSession session, String tenantId, String agentId,
                                            List<MindMapNode> allCognitive) {
        var competitionNodes = allCognitive.stream()
                                           .filter(n -> n.traits().contains(DriveProfileDerivationPhase.COMPETITION_TRAIT))
                                           .filter(n -> agentId.equals(n.properties().get("agent-id")))
                                           .toList();

        for (var node : competitionNodes) {
            String memoriesA = node.properties().getOrDefault("anchor-memories-a", "");
            String memoriesB = node.properties().getOrDefault("anchor-memories-b", "");
            if (memoriesA.isBlank() && memoriesB.isBlank()) {continue;}

            try {
                String discrimination = callDiscriminationLlm(session,
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

    private String callDiscriminationLlm(AgentSession session, String memoriesA, String memoriesB) {
        var userPrompt = String.format("""
                                       Two pulls often activate together in the same moment.
                                       
                                       Pull A was shaped by these experiences:
                                       %s
                                       
                                       Pull B was shaped by these experiences:
                                       %s
                                       
                                       Generate a discrimination criterion — how these two pulls \
                                       FEEL DIFFERENT in what they do to the body, where they draw attention, \
                                       how they shape the moment, and what they pull toward. Frame as \
                                       "How they differ:" followed by the criterion. \
                                       The criterion must work regardless of the current situation.""",
                                       memoriesA, memoriesB);

        var responseText = new StringBuilder();
        session.query(userPrompt).subscribe().asStream()
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

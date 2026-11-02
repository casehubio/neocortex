package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognitive.index.CognitiveProfileQuery;
import io.casehub.neocortex.cognitive.index.CorrelationStrength;
import io.casehub.neocortex.cognitive.index.DomainActivation;
import io.casehub.neocortex.cognitive.index.DomainActivationQuery;
import io.casehub.neocortex.memory.MemoryDomain;
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
import io.casehub.platform.api.identity.PrincipalId;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
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
@Priority(22)
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

                                                Personality scores are body calibration: high Callousness means the sympathy region \
                                                is quiet; high Suspiciousness means shoulders square before worry registers. \
                                                Attachment is the somatic ground state.

                                                START the description with where this pull lives in the body — use the assigned \
                                                body territory. Use discovery verbs (arrives, settles, pools, fizzes, shifts, \
                                                tightens, drops, spreads) not analytical verbs (calculates, assesses, determines, \
                                                considers, evaluates).

                                                Weave the two primary channels as the backbone. The four channels:
                                                - Somatic: where exactly, what sensation (tension, warmth, numbness, weight)
                                                - Attentional: what seized focus, what stopped being noticed
                                                - Temporal: compressed, stretched, frozen, urgent
                                                - Action tendency: what the body prepares to do before deciding
                                                Add the other two channels as texture, not sections.

                                                BODY TERRITORY: Each pull has an assigned body location. Describe it using \
                                                ONLY that territory. Never place a pull in another pull's territory.

                                                CONTRAST: When other pulls' body locations are listed, include explicit contrast \
                                                in the description: "Not the [other-location]. This is [body-territory] — [sensation]." \
                                                This is the single most important differentiator.

                                                Ground in specific formation memories where possible.
                                                Write in second person. Do not name the drive. 3-5 sentences.
                                                Respond with JSON only: {"description": "..."}""";

    private final MindMapStore store;
    private final CognitiveProfile cognitiveProfile;
    private final DomainActivation domainActivation;
    private volatile SessionPipeline sessionPipeline;

    private static final int MAX_ENTITIES = 5;
    private static final int MEMORY_LIMIT = 5;

    @Inject
    public DriveDescriptionGenerationPhase(MindMapStore store,
                                            Instance<CognitiveProfile> cognitiveProfileInstance,
                                            Instance<DomainActivation> domainActivationInstance) {
        this.store = store;
        this.cognitiveProfile = cognitiveProfileInstance.isUnsatisfied()
            ? null : cognitiveProfileInstance.get();
        this.domainActivation = domainActivationInstance.isUnsatisfied()
            ? null : domainActivationInstance.get();
    }

    DriveDescriptionGenerationPhase(MindMapStore store) {
        this.store            = store;
        this.cognitiveProfile = null;
        this.domainActivation = null;
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

        List<MindMapNode> relationalNodes = allCognitive.stream()
                                                        .filter(n -> "relational-schema".equals(n.properties().get("cognitiveKind")))
                                                        .toList();

        var kindCounts = allCognitive.stream()
                                     .map(n -> n.properties().getOrDefault("cognitiveKind", "none"))
                                     .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
        LOG.info("Drive description generation: " + drivesByAgent.size() + " agents, "
                 + drivesByAgent.values().stream().mapToInt(List::size).sum() + " drives, "
                 + formativeNodes.size() + " formative memories, "
                 + relationalNodes.size() + " relational schemas. Node kinds: " + kindCounts);

        var descTasks   = new java.util.ArrayList<java.util.function.Consumer<AgentSession>>();
        var discTasks   = new java.util.ArrayList<java.util.function.Consumer<AgentSession>>();
        int agentIndex  = 0;
        int totalAgents = drivesByAgent.size();
        for (var agentEntry : drivesByAgent.entrySet()) {
            agentIndex++;
            String            agentId    = agentEntry.getKey();
            List<MindMapNode> driveNodes = agentEntry.getValue();

            Set<String> describedKeys = allCognitive.stream()
                                                    .filter(n -> n.traits().contains(DESCRIPTION_TRAIT))
                                                    .filter(n -> agentId.equals(n.properties().get("agent-id")))
                                                    .map(n -> {
                                                        String dt   = n.properties().get("drive-type");
                                                        String role = n.properties().get("role");
                                                        return role != null ? dt + "-" + role : dt;
                                                    })
                                                    .filter(java.util.Objects::nonNull)
                                                    .collect(Collectors.toSet());

            List<MindMapNode> agentSchemas = relationalNodes.stream()
                                                            .filter(n -> agentId.equals(n.properties().get("agent-id")))
                                                            .toList();

            Set<String> agentRoles = agentSchemas.stream()
                                                 .map(n -> n.properties().get("rel.role"))
                                                 .filter(java.util.Objects::nonNull)
                                                 .collect(Collectors.toSet());

            Set<String> currentDriveTypes = driveNodes.stream()
                                                      .map(n -> n.properties().get("drive-type"))
                                                      .filter(java.util.Objects::nonNull)
                                                      .collect(Collectors.toSet());

            Set<String> requiredKeys = new java.util.HashSet<>(currentDriveTypes);
            for (String dt : currentDriveTypes) {
                for (String role : agentRoles) {
                    requiredKeys.add(dt + "-" + role);
                }
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

            if (agentMemories.isEmpty() && personalityNodes.isEmpty()) {continue;}

            String subgraphId = findCognitiveSubgraph(tenantId, agentId);
            if (subgraphId == null) {continue;}

            String entityContext = buildRelationalContext(tenantId, agentSchemas);
            String domainContext = buildDomainActivationContext(tenantId, agentId);
            String relationalContext = entityContext.isEmpty() && domainContext.isEmpty()
                ? "" : entityContext + domainContext;

            String currentHash = hashContext(relationalContext);
            boolean hashChanged = allCognitive.stream()
                .filter(n -> n.traits().contains(DESCRIPTION_TRAIT))
                .filter(n -> agentId.equals(n.properties().get("agent-id")))
                .findFirst()
                .map(n -> !currentHash.equals(n.properties().getOrDefault("relational-context-hash", "")))
                .orElse(false);

            if (describedKeys.containsAll(requiredKeys) && !hashChanged) {
                LOG.info("Description generation [" + agentIndex + "/" + totalAgents + "]: "
                         + agentId + " skipped — descriptions already exist");
                continue;
            }

            LOG.info("Description generation [" + agentIndex + "/" + totalAgents + "]: "
                     + agentId + " (" + driveNodes.size() + " drives, "
                     + agentMemories.size() + " memories, "
                     + agentSchemas.size() + " schemas"
                     + (hashChanged ? ", context hash changed" : "") + ")");

            var allDriveLocations = new java.util.HashMap<String, String>();
            for (var dn : driveNodes) {
                String dt = dn.properties().get("drive-type");
                String bl = dn.properties().get("body-location");
                if (dt != null && bl != null) { allDriveLocations.put(dt, bl); }
            }

            for (MindMapNode driveNode : driveNodes) {
                String driveType = driveNode.properties().get("drive-type");
                if (driveType == null || describedKeys.contains(driveType)) {continue;}

                List<MindMapNode> servingMemories = agentMemories.stream()
                                                                 .filter(m -> memoryServesDrive(m, driveType))
                                                                 .toList();

                String intensity = driveNode.properties().getOrDefault("intensity", "0.5");
                String bodyLocation = driveNode.properties().get("body-location");
                String channelEmphasis = driveNode.properties().get("channel-emphasis");

                var otherLocations = new java.util.HashMap<>(allDriveLocations);
                otherLocations.remove(driveType);

                descTasks.add(session -> {
                    long start = System.currentTimeMillis();
                    generateAndStore(session, tenantId, agentId, subgraphId,
                                     driveType, intensity, servingMemories, personalityNodes, attachmentNode, agentSchemas,
                                     bodyLocation, channelEmphasis, otherLocations, relationalContext);
                    LOG.info("  " + agentId + "/" + driveType + " (" + servingMemories.size()
                             + " memories, " + personalityNodes.size() + " facets, "
                             + agentSchemas.size() + " schemas, "
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
                                  List<MindMapNode> personalityNodes, MindMapNode attachmentNode,
                                  List<MindMapNode> agentSchemas,
                                  String bodyLocation, String channelEmphasis,
                                  Map<String, String> otherDriveLocations,
                                  String relationalContext) {
        try {
            String rawResponse = callLlm(session, driveType, intensity, servingMemories,
                                         personalityNodes, attachmentNode, agentSchemas,
                                         bodyLocation, channelEmphasis, otherDriveLocations,
                                         relationalContext);
            if (rawResponse == null || rawResponse.isBlank()) {return;}

            String description = parseDescription(rawResponse);
            if (description == null || description.isBlank()) {return;}

            var baseProps = new java.util.HashMap<String, String>();
            baseProps.put("agent-id", agentId);
            baseProps.put("drive-type", driveType);
            baseProps.put("drive-intensity", intensity);
            baseProps.put("generated-description", description);
            baseProps.put("source-memory-count", String.valueOf(servingMemories.size()));
            baseProps.put("relational-context-hash", hashContext(relationalContext));
            store.addNode(
                    NodeInput.of("drive-description-" + driveType, subgraphId)
                             .withTraits(Set.of(DESCRIPTION_TRAIT))
                             .withProperties(Map.copyOf(baseProps)),
                    tenantId);

            Map<String, String> targeted = parseTargeted(rawResponse);
            for (var entry : targeted.entrySet()) {
                String role         = entry.getKey();
                String targetedDesc = entry.getValue();
                if (targetedDesc == null || targetedDesc.isBlank()) {continue;}
                var props = new java.util.HashMap<String, String>();
                props.put("agent-id", agentId);
                props.put("drive-type", driveType);
                props.put("role", role);
                props.put("drive-intensity", intensity);
                props.put("generated-description", targetedDesc);
                props.put("relational-context-hash", hashContext(relationalContext));
                store.addNode(
                        NodeInput.of("drive-description-" + driveType + "-" + role, subgraphId)
                                 .withTraits(Set.of(DESCRIPTION_TRAIT))
                                 .withProperties(Map.copyOf(props)),
                        tenantId);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    agentId + "/" + driveType + ": description generation failed (non-fatal)", e);
        }
    }

    private String callLlm(AgentSession session, String driveType, String intensity,
                           List<MindMapNode> servingMemories,
                           List<MindMapNode> personalityNodes, MindMapNode attachmentNode,
                           List<MindMapNode> agentSchemas,
                           String bodyLocation, String channelEmphasis,
                           Map<String, String> otherDriveLocations,
                           String relationalContext) {
        var promptBuilder = new StringBuilder();
        promptBuilder.append("Drive type: ").append(driveType).append("\n");
        promptBuilder.append("Intensity: ").append(intensity).append(" (scale 0.0 to 1.0)\n");
        if (bodyLocation != null) {
            promptBuilder.append("Body territory: ").append(bodyLocation).append("\n");
        }
        if (channelEmphasis != null) {
            promptBuilder.append("Primary channels: ").append(channelEmphasis).append("\n");
        }
        promptBuilder.append("\n");

        if (!otherDriveLocations.isEmpty()) {
            promptBuilder.append("Other pulls this character has (for contrast — use ONLY this pull's territory):\n");
            for (var e : otherDriveLocations.entrySet()) {
                promptBuilder.append("  ").append(e.getKey()).append(" → ").append(e.getValue()).append("\n");
            }
            promptBuilder.append("\n");
        }

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

        if (agentSchemas != null && !agentSchemas.isEmpty()) {
            promptBuilder.append("Relationships this character has:\n");
            for (var schema : agentSchemas) {
                String role = schema.properties().get("rel.role");
                String desc = schema.properties().getOrDefault("rel.description", "");
                promptBuilder.append("  - ").append(role);
                if (!desc.isEmpty()) {promptBuilder.append(": ").append(desc);}
                promptBuilder.append("\n");
            }
            promptBuilder.append("\n");

            if (relationalContext != null && !relationalContext.isEmpty()) {
                promptBuilder.append(relationalContext).append("\n");
            }

            promptBuilder.append("Given this character's personality, memories, and relationships, describe how ")
                         .append("this pull TYPICALLY manifests in their body — without naming the pull. ")
                         .append("Weave all four channels together. Second person, no behavioral instructions.\n\n")
                         .append("Also describe how it feels differently when directed at each relationship type ")
                         .append("where this pull naturally activates. Not every pull targets every relationship.\n\n")
                         .append("Respond with JSON: {\"description\": \"...\", \"targeted\": {\"role\": \"...\"}}");
        } else {
            promptBuilder.append("Given this character's personality and formation memories, describe how this pull ")
                         .append("TYPICALLY manifests in their body — without naming the pull. ")
                         .append("Weave all four channels together. Second person, no behavioral instructions.");
        }

        String userPrompt = promptBuilder.toString();

        var responseText = new StringBuilder();
        session.query(userPrompt).subscribe().asStream()
               .filter(e -> e instanceof AgentEvent.TextDelta)
               .map(e -> ((AgentEvent.TextDelta) e).text())
               .forEach(responseText::append);

        return responseText.toString();
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

    private Map<String, String> parseTargeted(String json) {
        json = json.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
        }
        int idx = json.indexOf("\"targeted\"");
        if (idx < 0) {return Map.of();}
        int braceStart = json.indexOf('{', idx);
        if (braceStart < 0) {return Map.of();}
        int depth    = 0;
        int braceEnd = -1;
        for (int i = braceStart; i < json.length(); i++) {
            if (json.charAt(i) == '{') {depth++;} else if (json.charAt(i) == '}') {
                depth--;
                if (depth == 0) {
                    braceEnd = i;
                    break;
                }
            }
        }
        if (braceEnd < 0) {return Map.of();}
        String inner  = json.substring(braceStart + 1, braceEnd).strip();
        var    result = new java.util.HashMap<String, String>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                                            .compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                                            .matcher(inner);
        while (m.find()) {
            result.put(m.group(1), m.group(2).replace("\\\"", "\"").replace("\\n", "\n"));
        }
        return result;
    }


    private String buildRelationalContext(String tenantId, List<MindMapNode> agentSchemas) {
        if (cognitiveProfile == null || agentSchemas.isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        sb.append("Relational context for your pulls:\n\n");

        int     entityCount = 0;
        boolean anyContent  = false;
        for (var schema : agentSchemas) {
            if (entityCount >= MAX_ENTITIES) {break;}
            String targetId = schema.properties().get("target-id");
            String role     = schema.properties().get("rel.role");
            if (targetId == null || role == null) {continue;}

            var query = CognitiveProfileQuery.byName(targetId, tenantId)
                                             .withMemoryLimit(MEMORY_LIMIT);
            var knowledge = cognitiveProfile.resolve(query);
            if (knowledge.isEmpty()) {continue;}

            var ek = knowledge.get();
            sb.append("About ").append(ek.node().name());
            String type = ek.node().type();
            if (type != null && !type.isEmpty()) {sb.append(" (").append(type).append(")");}
            sb.append(":\n");

            var experienceMemories = ek.memories().getOrDefault(
                    new MemoryDomain("experience"), List.of());
            if (!experienceMemories.isEmpty()) {
                sb.append("  Recent memories:\n");
                for (var mem : experienceMemories) {
                    sb.append("    - ").append(mem.text()).append("\n");
                }
            }

            var trajectory = ek.trajectory();
            if (trajectory != null && trajectory.sampleCount() > 0) {
                sb.append("  Emotional trajectory: ").append(trajectory.trend())
                  .append(" (pleasure slope: ").append(
                          String.format("%.2f", trajectory.pleasureSlope()))
                  .append(")\n");
            }
            sb.append("\n");
            entityCount++;
            anyContent = true;
        }

        return anyContent ? sb.toString() : "";
    }

    private String buildDomainActivationContext(String tenantId, String agentId) {
        if (domainActivation == null) {return "";}

        var subgraphs = store.listSubgraphs(tenantId).stream()
                             .filter(sg -> SubgraphTypes.COGNITIVE.equals(sg.type()))
                             .toList();
        if (subgraphs.size() < 2) {return "";}

        var subgraphIds = subgraphs.stream()
                                   .map(sg -> sg.id())
                                   .collect(java.util.stream.Collectors.toSet());

        try {
            var query = new DomainActivationQuery(
                    PrincipalId.agent(agentId), subgraphIds, tenantId,
                    java.time.Instant.now().minus(java.time.Duration.ofDays(7)),
                    java.time.Instant.now(), null, Set.of(), null);

            var result = domainActivation.correlate(query);
            if (result.isEmpty()) {return "";}

            var correlations = result.get().correlations();
            if (correlations.isEmpty()) {return "";}

            var sb = new StringBuilder();
            sb.append("Cross-domain emotional patterns:\n");
            boolean any = false;
            for (var entry : correlations.entrySet()) {
                var pair = entry.getKey();
                var corr = entry.getValue();
                if (corr.strength() == CorrelationStrength.NONE) {continue;}

                String nameA = subgraphs.stream()
                                        .filter(sg -> sg.id().equals(pair.subgraphIdA()))
                                        .map(sg -> sg.name()).findFirst().orElse(pair.subgraphIdA());
                String nameB = subgraphs.stream()
                                        .filter(sg -> sg.id().equals(pair.subgraphIdB()))
                                        .map(sg -> sg.name()).findFirst().orElse(pair.subgraphIdB());

                sb.append("  Your emotional responses in \"")
                  .append(nameA).append("\" and \"")
                  .append(nameB).append("\" are ")
                  .append(corr.strength().name().toLowerCase())
                  .append(" correlated\n");
                any = true;
            }
            sb.append("\n");
            return any ? sb.toString() : "";
        } catch (Exception e) {
            LOG.log(Level.FINE, "Domain activation context skipped: " + e.getMessage());
            return "";
        }
    }


    static String hashContext(String relationalContext) {
        if (relationalContext == null || relationalContext.isEmpty()) {return "";}
        return Integer.toHexString(relationalContext.hashCode());
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

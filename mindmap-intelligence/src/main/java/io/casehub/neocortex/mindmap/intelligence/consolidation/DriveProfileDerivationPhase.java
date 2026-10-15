package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.caps.CapsConnection;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationClassifier;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Derives drive CAPS profiles from formation memories and detects drive competition.
 *
 * Literature: Barrett (2017) constructed emotion — concept = population of experiential
 * instances. Kashdan et al. (2015) — conceptual knowledge enables differentiation.
 * Semerari et al. (2003) — S6-level narrative grounding for metacognitive differentiation.
 *
 * See research doc §10.10.6-7, decision D12.
 */
@ApplicationScoped
@Priority(20)
public class DriveProfileDerivationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(
        DriveProfileDerivationPhase.class.getName());

    static final String PROFILE_TRAIT = "DriveProfileInferred";
    static final String COMPETITION_TRAIT = "DriveCompetitionInferred";

    private final MindMapStore store;
    private final SituationClassifier classifier;
    private final CapsTopology topology;

    @Inject
    public DriveProfileDerivationPhase(MindMapStore store,
                                       Instance<SituationClassifier> classifier,
                                       Instance<CapsTopology> topology) {
        this.store = store;
        this.classifier = classifier.isResolvable() ? classifier.get() : null;
        this.topology = topology.isResolvable() ? topology.get() : null;
    }

    public DriveProfileDerivationPhase(MindMapStore store,
                                       SituationClassifier classifier,
                                       CapsTopology topology) {
        this.store = store;
        this.classifier = classifier;
        this.topology = topology;
    }

    @Override
    public String name() {
        return "drive-profile-derivation";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
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

        Set<String> agentsWithProfiles = allCognitive.stream()
            .filter(n -> n.traits().contains(PROFILE_TRAIT))
            .map(n -> n.properties().get("agent-id"))
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        for (var agentEntry : drivesByAgent.entrySet()) {
            String            agentId    = agentEntry.getKey();
            List<MindMapNode> driveNodes = agentEntry.getValue();

            if (agentsWithProfiles.contains(agentId)) {
                LOG.info("drive-profile-derivation [" + agentId + "]: skipped — profiles already derived");
                continue;
            }

            List<MindMapNode> agentMemories = formativeNodes.stream()
                                                            .filter(n -> agentId.equals(n.properties().get("agent-id")))
                                                            .toList();

            if (agentMemories.isEmpty()) {continue;}

            Map<String, DriveProfile> profiles   = deriveProfiles(driveNodes, agentMemories);
            String                    subgraphId = findCognitiveSubgraph(tenantId, agentId);
            if (subgraphId == null) {continue;}

            storeProfiles(tenantId, agentId, subgraphId, profiles);
            detectAndStoreCompetition(tenantId, agentId, subgraphId, profiles, agentMemories);

            if (!profiles.isEmpty()) {
                var profileSummary = profiles.entrySet().stream()
                    .map(e -> e.getKey() + "(" + e.getValue().memoryExcerpts.size() + " memories)")
                    .collect(Collectors.joining(", "));
                LOG.info("drive-profile-derivation [" + agentId + "]: " + profiles.size()
                    + " profiles — " + profileSummary
                    + (classifier != null ? " [CAPS active]" : " [catalogue fallback]"));
            }
        }
    }

    private Map<String, DriveProfile> deriveProfiles(List<MindMapNode> driveNodes,
                                                     List<MindMapNode> formativeNodes) {
        Map<String, DriveProfile> profiles = new HashMap<>();

        for (MindMapNode driveNode : driveNodes) {
            String driveType = driveNode.properties().get("drive-type");
            if (driveType == null) {continue;}

            List<MindMapNode> servingMemories = formativeNodes.stream()
                                                              .filter(m -> memoryServesDrive(m, driveType))
                                                              .toList();

            if (servingMemories.isEmpty()) {continue;}

            Map<String, Double> inputActivations = new HashMap<>();
            List<String>        memoryExcerpts   = new ArrayList<>();

            for (MindMapNode memory : servingMemories) {
                String episodeText = memory.name();

                if (classifier != null) {
                    List<SituationActivation> activations = classifier.classify(episodeText, Map.of());
                    for (SituationActivation sa : activations) {
                        inputActivations.merge(sa.nodeId(), sa.confidence(), Math::max);
                    }
                }

                memoryExcerpts.add(excerpt(episodeText));
            }

            String description = driveNode.properties().get("description");
            if (classifier != null && description != null && !description.isBlank()) {
                List<SituationActivation> descActivations = classifier.classify(description, Map.of());
                for (SituationActivation sa : descActivations) {
                    inputActivations.merge(sa.nodeId(), sa.confidence(), Math::max);
                }
            }

            Set<String> mediatingNodes = topology != null
                                         ? traceToMediating(inputActivations.keySet())
                                         : Set.of();

            profiles.put(driveType, new DriveProfile(
                    driveType, inputActivations, mediatingNodes, memoryExcerpts));
        }

        return profiles;
    }

    private boolean memoryServesDrive(MindMapNode memoryNode, String driveType) {
        String situationTypes = memoryNode.properties().get("situation-types");
        if (situationTypes == null) return false;
        return Arrays.asList(situationTypes.split(",")).contains(driveType);
    }

    private Set<String> traceToMediating(Set<String> inputNodeIds) {
        Set<String> mediating = new HashSet<>();
        for (CapsConnection conn : topology.connections()) {
            if (inputNodeIds.contains(conn.from())) {
                var targetNode = topology.nodes().get(conn.to());
                if (targetNode != null && targetNode.type() == NodeType.MEDIATING) {
                    mediating.add(conn.to());
                }
            }
        }
        return mediating;
    }

    private void detectAndStoreCompetition(String tenantId, String agentId,
                                           String subgraphId,
                                           Map<String, DriveProfile> profiles,
                                           List<MindMapNode> agentMemories) {
        List<String> driveTypes = new ArrayList<>(profiles.keySet());

        for (int i = 0; i < driveTypes.size(); i++) {
            for (int j = i + 1; j < driveTypes.size(); j++) {
                DriveProfile a = profiles.get(driveTypes.get(i));
                DriveProfile b = profiles.get(driveTypes.get(j));

                Set<String> sharedMediating;
                Set<String> uniqueMediatingA;
                Set<String> uniqueMediatingB;

                if (!a.mediatingNodes.isEmpty() || !b.mediatingNodes.isEmpty()) {
                    sharedMediating = new HashSet<>(a.mediatingNodes);
                    sharedMediating.retainAll(b.mediatingNodes);
                    if (sharedMediating.isEmpty()) {continue;}
                    uniqueMediatingA = new HashSet<>(a.mediatingNodes);
                    uniqueMediatingA.removeAll(sharedMediating);
                    uniqueMediatingB = new HashSet<>(b.mediatingNodes);
                    uniqueMediatingB.removeAll(sharedMediating);
                } else {
                    Set<String> catalogueA      = catalogueEntries(agentMemories, a.driveType);
                    Set<String> catalogueB      = catalogueEntries(agentMemories, b.driveType);
                    Set<String> sharedCatalogue = new HashSet<>(catalogueA);
                    sharedCatalogue.retainAll(catalogueB);
                    if (sharedCatalogue.isEmpty()) {continue;}
                    sharedMediating  = sharedCatalogue;
                    uniqueMediatingA = new HashSet<>(catalogueA);
                    uniqueMediatingA.removeAll(sharedCatalogue);
                    uniqueMediatingB = new HashSet<>(catalogueB);
                    uniqueMediatingB.removeAll(sharedCatalogue);
                }

                String diffText = composeDifferentiationText(a, b, uniqueMediatingA, uniqueMediatingB);

                var props = Map.of(
                        "agent-id", agentId,
                        "drive-a", a.driveType,
                        "drive-b", b.driveType,
                        "shared-mediating", String.join(",", sharedMediating),
                        "unique-mediating-a", String.join(",", uniqueMediatingA),
                        "unique-mediating-b", String.join(",", uniqueMediatingB),
                        "differentiation-text", diffText,
                        "anchor-memories-a", String.join("|", a.memoryExcerpts),
                        "anchor-memories-b", String.join("|", b.memoryExcerpts));

                store.addNode(
                        NodeInput.of("competition-" + a.driveType + "-" + b.driveType, subgraphId)
                                 .withTraits(Set.of(COMPETITION_TRAIT))
                                 .withProperties(props),
                        tenantId);

                LOG.info("drive-competition [" + agentId + "]: "
                    + a.driveType + " vs " + b.driveType
                    + " — shared=" + String.join(",", sharedMediating)
                    + (a.mediatingNodes.isEmpty() ? " [catalogue]" : " [CAPS mediating]"));
            }
        }
    }

    private Set<String> catalogueEntries(List<MindMapNode> memories, String driveType) {
        return memories.stream()
                       .filter(m -> memoryServesDrive(m, driveType))
                       .map(m -> m.properties().get("catalogue-entry-id"))
                       .filter(c -> c != null && !c.isBlank())
                       .collect(Collectors.toSet());
    }


    private String composeDifferentiationText(DriveProfile a, DriveProfile b,
                                               Set<String> uniqueA, Set<String> uniqueB) {
        var sb = new StringBuilder();
        sb.append("How they differ:\n");

        if (!a.memoryExcerpts.isEmpty()) {
            sb.append("One pull traces through ").append(a.memoryExcerpts.getFirst());
            if (a.memoryExcerpts.size() > 1) {
                sb.append(", ").append(a.memoryExcerpts.get(1));
            }
            sb.append(".\n");
        }

        if (!b.memoryExcerpts.isEmpty()) {
            sb.append("The other traces through ").append(b.memoryExcerpts.getFirst());
            if (b.memoryExcerpts.size() > 1) {
                sb.append(", ").append(b.memoryExcerpts.get(1));
            }
            sb.append(".\n");
        }

        sb.append("Both are real. They're not the same.");
        return sb.toString();
    }

    private void storeProfiles(String tenantId, String agentId,
                                String subgraphId,
                                Map<String, DriveProfile> profiles) {
        for (var entry : profiles.entrySet()) {
            DriveProfile profile = entry.getValue();
            String inputsJson = profile.inputActivations.entrySet().stream()
                .map(e -> e.getKey() + ":" + String.format("%.3f", e.getValue()))
                .collect(Collectors.joining(","));

            store.addNode(
                NodeInput.of("drive-profile-" + profile.driveType, subgraphId)
                    .withTraits(Set.of(PROFILE_TRAIT))
                    .withProperties(Map.of(
                        "agent-id", agentId,
                        "drive-type", profile.driveType,
                        "inferred-inputs", inputsJson,
                        "mediating-nodes", String.join(",", profile.mediatingNodes),
                        "source-memory-count", String.valueOf(profile.memoryExcerpts.size()))),
                tenantId);
        }
    }

    private void removeExistingArtifacts(String tenantId, String agentId,
                                          List<MindMapNode> allCognitive) {
        allCognitive.stream()
            .filter(n -> n.traits().contains(PROFILE_TRAIT) || n.traits().contains(COMPETITION_TRAIT))
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

    private static String excerpt(String text) {
        if (text == null) return "";
        if (text.length() <= 80) return text;
        int end = text.lastIndexOf(' ', 80);
        if (end < 40) end = 80;
        return text.substring(0, end) + "...";
    }

    record DriveProfile(String driveType,
                        Map<String, Double> inputActivations,
                        Set<String> mediatingNodes,
                        List<String> memoryExcerpts) {}
}

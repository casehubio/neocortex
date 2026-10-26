package io.casehub.neocortex.caps.llm.eval;

import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Seeds an {@link InMemoryMindMapStore} from {@link EvalCharacterData}, replicating
 * the mindmap seeding pattern from ManorCognitiveSeeder and FocusedDriveEvalTest
 * without any manor dependency.
 */
final class EvalMindMapSeeder {

    private static final Pattern INLINE_ANNOTATION =
            Pattern.compile("\\{\\{serves:\\s*([\\w-]+)\\|\\w+:.*?\\}\\}");

    private EvalMindMapSeeder() {}

    static String seedCharacter(String agentId, EvalCharacterData data,
                                InMemoryMindMapStore store, String tenant) {
        return seedCharacter(agentId, data, store, tenant, null);
    }

    static String seedCharacter(String agentId, EvalCharacterData data,
                                InMemoryMindMapStore store, String tenant,
                                Map<String, String> driveProfiles) {
        var sgId = store.createSubgraph(
                new SubgraphInput("cognitive-" + agentId, SubgraphTypes.COGNITIVE, null), tenant);

        seedDrives(agentId, data, store, sgId, tenant);
        seedFormationMemories(agentId, data, store, sgId, tenant);

        if (driveProfiles != null) {
            seedProfiles(agentId, driveProfiles, store, sgId, tenant);
        }

        seedRelationalSchemas(agentId, data, store, sgId, tenant);

        return sgId;
    }

    private static void seedDrives(String agentId, EvalCharacterData data,
                                   InMemoryMindMapStore store, String sgId, String tenant) {
        for (var drive : data.drives()) {
            store.addNode(NodeInput.of(drive.type(), sgId)
                    .withProperties(Map.of(
                            "cognitiveKind", "drive-intensity",
                            "agent-id", agentId,
                            "drive-type", drive.type(),
                            "intensity", String.valueOf(drive.intensity()),
                            "description", drive.description())), tenant);
        }
    }

    private static void seedFormationMemories(String agentId, EvalCharacterData data,
                                               InMemoryMindMapStore store, String sgId, String tenant) {
        for (var mem : data.formationMemories()) {
            var driveTypes = new ArrayList<String>();
            driveTypes.add("formation");
            for (var ann : mem.annotations()) {
                if (!driveTypes.contains(ann.drive())) driveTypes.add(ann.drive());
            }
            store.addNode(NodeInput.of(mem.episode(), sgId)
                    .withProperties(Map.of(
                            "cognitiveKind", "formative-experience",
                            "agent-id", agentId,
                            "situation-types", String.join(",", driveTypes))), tenant);
        }
    }

    private static void seedProfiles(String agentId, Map<String, String> profiles,
                                     InMemoryMindMapStore store, String sgId, String tenant) {
        for (var entry : profiles.entrySet()) {
            store.addNode(NodeInput.of("drive-profile-" + entry.getKey(), sgId)
                    .withTraits(Set.of("DriveProfileInferred"))
                    .withProperties(Map.of(
                            "agent-id", agentId,
                            "drive-type", entry.getKey(),
                            "inferred-inputs", entry.getValue())), tenant);
        }
    }

    private static void seedRelationalSchemas(String agentId, EvalCharacterData data,
                                               InMemoryMindMapStore store, String sgId, String tenant) {
        for (var schema : data.relationalSchemas()) {
            store.addNode(NodeInput.of("rel-" + agentId + "-" + schema.target(), sgId)
                    .withProperties(Map.of(
                            "cognitiveKind", "relational-schema",
                            "agent-id", agentId,
                            "target-id", schema.target(),
                            "schema", schema.description())), tenant);
        }
    }

    static void seedDifferentiation(String agentId, InMemoryMindMapStore store,
                                    String sgId, String tenant,
                                    String driveA, String driveB, String text) {
        store.addNode(NodeInput.of("comp-" + driveA + "-" + driveB, sgId)
                .withTraits(Set.of("DriveCompetitionInferred"))
                .withProperties(Map.of(
                        "agent-id", agentId,
                        "drive-a", driveA,
                        "drive-b", driveB,
                        "differentiation-text", text)), tenant);
    }

    static void seedNearbyPresence(String nearbyId, InMemoryMindMapStore store,
                                   String sgId, String tenant) {
        store.addNode(NodeInput.of("presence-" + nearbyId, sgId)
                .withProperties(Map.of(
                        "cognitiveKind", "drive-intensity",
                        "agent-id", nearbyId,
                        "drive-type", "presence",
                        "intensity", "0.5",
                        "description", "nearby character")), tenant);
    }

    static void seedGeneratedDescription(String agentId, String driveType, double intensity,
                                          String description, InMemoryMindMapStore store,
                                          String sgId, String tenant) {
        store.addNode(NodeInput.of("drive-description-" + driveType, sgId)
                .withTraits(Set.of("DriveDescriptionGenerated"))
                .withProperties(Map.of(
                        "agent-id", agentId,
                        "drive-type", driveType,
                        "drive-intensity", String.valueOf(intensity),
                        "generated-description", description)), tenant);
    }
}

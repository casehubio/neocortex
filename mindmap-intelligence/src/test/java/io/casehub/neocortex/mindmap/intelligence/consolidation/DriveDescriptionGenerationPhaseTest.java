package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class DriveDescriptionGenerationPhaseTest {

    private InMemoryMindMapStore store;
    private DriveDescriptionGenerationPhase phase;
    private String cognitiveSubgraphId;
    private List<AgentSessionConfig> capturedConfigs;
    private static final String TENANT = "t1";
    private static final String AGENT = "peter-perfect";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        capturedConfigs = new CopyOnWriteArrayList<>();

        AgentProvider provider = new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                capturedConfigs.add(config);
                return Multi.createFrom().items(
                    new AgentEvent.TextDelta("{\"description\": \"A warmth rises through your chest, "),
                    new AgentEvent.TextDelta("spreading into your shoulders.\"}"));
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("not needed in test");
            }
        };

        phase = new DriveDescriptionGenerationPhase(store, provider);

        cognitiveSubgraphId = store.createSubgraph(
            new SubgraphInput("cognitive-" + AGENT, SubgraphTypes.COGNITIVE, null), TENANT);
    }

    @Test
    void phaseNameIsDriveDescriptionGeneration() {
        assertThat(phase.name()).isEqualTo("drive-description-generation");
    }

    @Test
    void skipsWhenNoAgentProvider() {
        var noLlmPhase = new DriveDescriptionGenerationPhase(store, (AgentProvider) null);
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        noLlmPhase.run(TENANT, List.of());

        assertThat(findDescriptionNodes()).isEmpty();
    }

    @Test
    void skipsWhenNoDriveNodes() {
        phase.run(TENANT, List.of());
        assertThat(findDescriptionNodes()).isEmpty();
    }

    @Test
    void skipsWhenNoFormationMemories() {
        seedDriveNode("gallantry", 0.9);

        phase.run(TENANT, List.of());

        assertThat(findDescriptionNodes()).isEmpty();
    }

    @Test
    void generatesDescriptionForDriveWithMemories() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        phase.run(TENANT, List.of());

        var descriptions = findDescriptionNodes();
        assertThat(descriptions).hasSize(1);
        assertThat(descriptions.getFirst().property("drive-type")).hasValue("gallantry");
        assertThat(descriptions.getFirst().property("agent-id")).hasValue(AGENT);
        assertThat(descriptions.getFirst().property("generated-description")).isPresent();
        assertThat(descriptions.getFirst().property("generated-description").orElse(""))
            .contains("warmth rises through your chest");
    }

    @Test
    void promptIncludesFormationMemoriesAndDriveContext() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        phase.run(TENANT, List.of());

        assertThat(capturedConfigs).hasSize(1);
        var config = capturedConfigs.getFirst();
        assertThat(config.userPrompt()).contains("gallantry");
        assertThat(config.userPrompt()).contains("0.9");
        assertThat(config.userPrompt()).contains("knelt to help the stranger");
    }

    @Test
    void generatesDescriptionPerDrive() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.7);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");
        seedFormationMemory("You practised until you earned the grade", "proving-worth");

        phase.run(TENANT, List.of());

        var descriptions = findDescriptionNodes();
        assertThat(descriptions).hasSize(2);
        var driveTypes = descriptions.stream()
            .map(n -> n.property("drive-type").orElse(""))
            .toList();
        assertThat(driveTypes).containsExactlyInAnyOrder("gallantry", "proving-worth");
    }

    @Test
    void replacesExistingDescriptionsOnRerun() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        phase.run(TENANT, List.of());
        assertThat(findDescriptionNodes()).hasSize(1);

        phase.run(TENANT, List.of());
        assertThat(findDescriptionNodes()).hasSize(1);
    }

    @Test
    void includesIntensityInStoredArtifact() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        phase.run(TENANT, List.of());

        var desc = findDescriptionNodes().getFirst();
        assertThat(desc.property("drive-intensity")).hasValue("0.9");
    }

    @Test
    void skipsDrivesWithNoServingMemories() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("scheming", 0.6);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        phase.run(TENANT, List.of());

        var descriptions = findDescriptionNodes();
        assertThat(descriptions).hasSize(1);
        assertThat(descriptions.getFirst().property("drive-type")).hasValue("gallantry");
    }

    // --- helpers ---

    private void seedDriveNode(String driveType, double intensity) {
        store.addNode(
            NodeInput.of(driveType, cognitiveSubgraphId)
                .withProperties(Map.of(
                    "cognitiveKind", "drive-intensity",
                    "agent-id", AGENT,
                    "drive-type", driveType,
                    "intensity", String.valueOf(intensity),
                    "description", "Drive: " + driveType)),
            TENANT);
    }

    private void seedFormationMemory(String episodeText, String driveType) {
        store.addNode(
            NodeInput.of(episodeText, cognitiveSubgraphId)
                .withProperties(Map.of(
                    "source-memory-id", "mem-" + episodeText.hashCode(),
                    "agent-id", AGENT,
                    "cognitiveKind", "formative-experience",
                    "situation-types", "formation,childhood," + driveType)),
            TENANT);
    }

    private List<MindMapNode> findDescriptionNodes() {
        return store.search(MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.COGNITIVE))
            .stream()
            .filter(n -> n.traits().contains(DriveDescriptionGenerationPhase.DESCRIPTION_TRAIT))
            .toList();
    }
}

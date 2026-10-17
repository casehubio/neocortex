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
                if (config.systemPrompt().contains("discrimination")) {
                    return Multi.createFrom().items(
                        new AgentEvent.TextDelta("{\"discrimination\": \"How they differ: One pull is about what your hands say. "),
                        new AgentEvent.TextDelta("The other is about what your hands can do. Notice which one moved your hands.\"}"));
                }
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
    void skipsExistingDescriptionsOnRerun() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        phase.run(TENANT, List.of());
        assertThat(findDescriptionNodes()).hasSize(1);
        int callsAfterFirstRun = capturedConfigs.size();

        phase.run(TENANT, List.of());
        assertThat(findDescriptionNodes()).hasSize(1);
        assertThat(capturedConfigs).as("no LLM calls on rerun — descriptions already exist")
            .hasSize(callsAfterFirstRun);
    }

    @Test
    void descriptionsPreservedAcrossReruns() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.7);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");
        seedFormationMemory("You practised until you earned the grade", "proving-worth");

        phase.run(TENANT, List.of());
        var firstRunDescriptions = findDescriptionNodes();
        assertThat(firstRunDescriptions).hasSize(2);

        phase.run(TENANT, List.of());
        var secondRunDescriptions = findDescriptionNodes();
        assertThat(secondRunDescriptions).hasSize(2);
        assertThat(secondRunDescriptions.stream().map(MindMapNode::id).toList())
            .as("same node IDs — originals preserved, not replaced")
            .containsExactlyInAnyOrderElementsOf(
                firstRunDescriptions.stream().map(MindMapNode::id).toList());
    }

    @Test
    void partialCoverageGeneratesOnlyMissingDrives() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.7);
        seedDriveNode("protection", 0.8);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");
        seedFormationMemory("You practised until you earned the grade", "proving-worth");
        seedFormationMemory("You noticed the loose railing before anyone else", "protection");

        phase.run(TENANT, List.of());
        assertThat(findDescriptionNodes()).hasSize(3);
        int callsForFullRun = capturedConfigs.size();

        // Simulate partial coverage: remove one description (as if interrupted)
        var descriptions = findDescriptionNodes();
        var protectionDesc = descriptions.stream()
                                         .filter(n -> "protection".equals(n.properties().get("drive-type")))
                                         .findFirst().orElseThrow();
        store.eraseNode(protectionDesc.id(), TENANT);
        assertThat(findDescriptionNodes()).hasSize(2);

        phase.run(TENANT, List.of());
        assertThat(findDescriptionNodes())
                .as("partial rerun should generate only the missing drive")
                .hasSize(3);
        assertThat(capturedConfigs)
                .as("only 1 new LLM call for the missing drive, not 3")
                .hasSize(callsForFullRun + 1);
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
    void generatesGenericDescriptionForDrivesWithNoServingMemories() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("scheming", 0.6);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        phase.run(TENANT, List.of());

        var descriptions = findDescriptionNodes();
        assertThat(descriptions).hasSize(2);
        var driveTypes = descriptions.stream()
            .map(n -> n.property("drive-type").orElse(""))
            .toList();
        assertThat(driveTypes).containsExactlyInAnyOrder("gallantry", "scheming");
    }

    @Test
    void updatesCompetitionWithSomaticDiscrimination() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.7);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");
        seedFormationMemory("You practised until you earned the grade", "proving-worth");

        store.addNode(
            NodeInput.of("competition-gallantry-proving-worth", cognitiveSubgraphId)
                .withTraits(java.util.Set.of(DriveProfileDerivationPhase.COMPETITION_TRAIT))
                .withProperties(Map.of(
                    "agent-id", AGENT,
                    "drive-a", "gallantry", "drive-b", "proving-worth",
                    "differentiation-text", "Original narrative text",
                    "anchor-memories-a", "father knelt to help",
                    "anchor-memories-b", "practised until dark")),
            TENANT);

        phase.run(TENANT, List.of());

        var competitions = store.search(MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.COGNITIVE))
            .stream()
            .filter(n -> n.traits().contains(DriveProfileDerivationPhase.COMPETITION_TRAIT))
            .toList();
        assertThat(competitions).hasSize(1);
        String diffText = competitions.getFirst().property("differentiation-text").orElse("");
        assertThat(diffText).isNotEqualTo("Original narrative text");
        assertThat(diffText).isNotBlank();
    }

    @Test
    void skipsDiscriminationWhenNoCompetitionArtifacts() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        phase.run(TENANT, List.of());

        assertThat(findDescriptionNodes()).hasSize(1);
    }

    @Test
    void crossPhasePipeline_derivationToDiscrimination() {
        var derivationPhase = new DriveProfileDerivationPhase(store,
                                                              (io.casehub.neocortex.caps.SituationClassifier) null,
                                                              (io.casehub.neocortex.caps.CapsTopology) null);

        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.7);
        seedFormationMemoryWithCatalogue("Your father knelt to help the stranger",
                                         "gallantry", "other_safety");
        seedFormationMemoryWithCatalogue("She struggled and you helped her up",
                                         "proving-worth", "other_safety");

        derivationPhase.run(TENANT, List.of());

        var competitionsBefore = store.search(MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.COGNITIVE))
                                      .stream()
                                      .filter(n -> n.traits().contains(DriveProfileDerivationPhase.COMPETITION_TRAIT))
                                      .toList();
        assertThat(competitionsBefore).hasSize(1);
        String narrativeText = competitionsBefore.getFirst().property("differentiation-text").orElse("");
        assertThat(narrativeText).contains("How they differ");

        phase.run(TENANT, List.of());

        var competitionsAfter = store.search(MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.COGNITIVE))
                                     .stream()
                                     .filter(n -> n.traits().contains(DriveProfileDerivationPhase.COMPETITION_TRAIT))
                                     .toList();
        assertThat(competitionsAfter).hasSize(1);
        String updatedText = competitionsAfter.getFirst().property("differentiation-text").orElse("");
        assertThat(updatedText).isNotEqualTo(narrativeText);
        assertThat(updatedText).contains("hands");
    }

    @Test
    void descriptionPromptRequestsMultiChannelExperience() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        phase.run(TENANT, List.of());

        assertThat(capturedConfigs).isNotEmpty();
        var systemPrompt = capturedConfigs.getFirst().systemPrompt();
        assertThat(systemPrompt).as("must request attentional channel")
                                .contains("Attentional");
        assertThat(systemPrompt).as("must request temporal channel")
                                .contains("Temporal");
        assertThat(systemPrompt).as("must request action tendency channel")
                                .contains("Action tendency");
        assertThat(systemPrompt).as("must not restrict to somatic-only")
                                .doesNotContain("somatic only");
    }

    @Test
    void discriminationPromptRequestsMultiChannelCriteria() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.7);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");
        seedFormationMemory("You practised until you earned the grade", "proving-worth");

        store.addNode(
                NodeInput.of("competition-gallantry-proving-worth", cognitiveSubgraphId)
                         .withTraits(java.util.Set.of(DriveProfileDerivationPhase.COMPETITION_TRAIT))
                         .withProperties(Map.of(
                                 "agent-id", AGENT,
                                 "drive-a", "gallantry", "drive-b", "proving-worth",
                                 "differentiation-text", "narrative",
                                 "anchor-memories-a", "father knelt to help",
                                 "anchor-memories-b", "practised until dark")),
                TENANT);

        phase.run(TENANT, List.of());

        var discriminationConfigs = capturedConfigs.stream()
                                                   .filter(c -> c.systemPrompt().contains("discrimination"))
                                                   .toList();
        assertThat(discriminationConfigs).isNotEmpty();

        var systemPrompt = discriminationConfigs.getFirst().systemPrompt();
        assertThat(systemPrompt).as("must request attentional channel")
                                .contains("Attentional");
        assertThat(systemPrompt).as("must request action tendency channel")
                                .contains("Action tendency");
        assertThat(systemPrompt).as("must not restrict to somatic-only")
                                .doesNotContain("somatic only");
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

    private void seedFormationMemoryWithCatalogue(String episodeText, String driveType,
                                                  String catalogueEntry) {
        store.addNode(
                NodeInput.of(episodeText, cognitiveSubgraphId)
                         .withProperties(Map.of(
                                 "source-memory-id", "mem-" + episodeText.hashCode(),
                                 "agent-id", AGENT,
                                 "cognitiveKind", "formative-experience",
                                 "situation-types", "formation,childhood," + driveType,
                                 "catalogue-entry-id", catalogueEntry)),
                TENANT);
    }


    private List<MindMapNode> findDescriptionNodes() {
        return store.search(MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.COGNITIVE))
            .stream()
            .filter(n -> n.traits().contains(DriveDescriptionGenerationPhase.DESCRIPTION_TRAIT))
            .toList();
    }
}

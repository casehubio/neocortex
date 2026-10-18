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

class SelfStandardDerivationPhaseTest {

    private InMemoryMindMapStore store;
    private SelfStandardDerivationPhase phase;
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
                    new AgentEvent.TextDelta("{\"self_standard\": \"When you meet this standard, a quiet warmth settles behind your sternum. "),
                    new AgentEvent.TextDelta("When you fall short, a weight gathers at the base of your throat.\"}"));
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("not needed in test");
            }
        };

        phase = new SelfStandardDerivationPhase(store, provider);

        cognitiveSubgraphId = store.createSubgraph(
            new SubgraphInput("cognitive-" + AGENT, SubgraphTypes.COGNITIVE, null), TENANT);
    }

    @Test
    void phaseNameIsSelfStandardDerivation() {
        assertThat(phase.name()).isEqualTo("self-standard-derivation");
    }

    @Test
    void skipsWhenNoAgentProvider() {
        var noLlmPhase = new SelfStandardDerivationPhase(store, (AgentProvider) null);
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        noLlmPhase.run(TENANT, List.of());

        assertThat(findSelfStandardNodes()).isEmpty();
    }

    @Test
    void skipsWhenNoDriveNodes() {
        phase.run(TENANT, List.of());
        assertThat(findSelfStandardNodes()).isEmpty();
    }

    @Test
    void skipsWhenNoFormationMemories() {
        seedDriveNode("gallantry", 0.9);

        phase.run(TENANT, List.of());

        assertThat(findSelfStandardNodes()).isEmpty();
    }

    @Test
    void generatesSelfStandardForDriveWithMemories() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        phase.run(TENANT, List.of());

        var selfStandards = findSelfStandardNodes();
        assertThat(selfStandards).hasSize(1);
        assertThat(selfStandards.getFirst().property("drive-type")).hasValue("gallantry");
        assertThat(selfStandards.getFirst().property("agent-id")).hasValue(AGENT);
        assertThat(selfStandards.getFirst().property("self-standard")).isPresent();
        assertThat(selfStandards.getFirst().property("self-standard").orElse(""))
            .contains("quiet warmth settles behind your sternum");
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
    void generatesSelfStandardPerDrive() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.7);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");
        seedFormationMemory("You practised until you earned the grade", "proving-worth");

        phase.run(TENANT, List.of());

        var selfStandards = findSelfStandardNodes();
        assertThat(selfStandards).hasSize(2);
        var driveTypes = selfStandards.stream()
            .map(n -> n.property("drive-type").orElse(""))
            .toList();
        assertThat(driveTypes).containsExactlyInAnyOrder("gallantry", "proving-worth");
    }

    @Test
    void skipsExistingSelfStandardsOnRerun() {
        seedDriveNode("gallantry", 0.9);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");

        phase.run(TENANT, List.of());
        assertThat(findSelfStandardNodes()).hasSize(1);
        int callsAfterFirstRun = capturedConfigs.size();

        phase.run(TENANT, List.of());
        assertThat(findSelfStandardNodes()).hasSize(1);
        assertThat(capturedConfigs).as("no LLM calls on rerun — self-standards already exist")
            .hasSize(callsAfterFirstRun);
    }

    @Test
    void selfStandardsPreservedAcrossReruns() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.7);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");
        seedFormationMemory("You practised until you earned the grade", "proving-worth");

        phase.run(TENANT, List.of());
        var firstRunNodes = findSelfStandardNodes();
        assertThat(firstRunNodes).hasSize(2);

        phase.run(TENANT, List.of());
        var secondRunNodes = findSelfStandardNodes();
        assertThat(secondRunNodes).hasSize(2);
        assertThat(secondRunNodes.stream().map(MindMapNode::id).toList())
            .as("same node IDs — originals preserved, not replaced")
            .containsExactlyInAnyOrderElementsOf(
                firstRunNodes.stream().map(MindMapNode::id).toList());
    }

    @Test
    void partialCoverageGeneratesOnlyMissing() {
        seedDriveNode("gallantry", 0.9);
        seedDriveNode("proving-worth", 0.7);
        seedDriveNode("protection", 0.8);
        seedFormationMemory("Your father knelt to help the stranger", "gallantry");
        seedFormationMemory("You practised until you earned the grade", "proving-worth");
        seedFormationMemory("You noticed the loose railing before anyone else", "protection");

        phase.run(TENANT, List.of());
        assertThat(findSelfStandardNodes()).hasSize(3);
        int callsForFullRun = capturedConfigs.size();

        // Simulate partial coverage: remove one self-standard (as if interrupted)
        var nodes = findSelfStandardNodes();
        var protectionNode = nodes.stream()
                                   .filter(n -> "protection".equals(n.properties().get("drive-type")))
                                   .findFirst().orElseThrow();
        store.eraseNode(protectionNode.id(), TENANT);
        assertThat(findSelfStandardNodes()).hasSize(2);

        phase.run(TENANT, List.of());
        assertThat(findSelfStandardNodes())
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

        var node = findSelfStandardNodes().getFirst();
        assertThat(node.property("drive-intensity")).hasValue("0.9");
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

    private List<MindMapNode> findSelfStandardNodes() {
        return store.search(MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.COGNITIVE))
            .stream()
            .filter(n -> n.traits().contains(SelfStandardDerivationPhase.SELF_STANDARD_TRAIT))
            .toList();
    }
}

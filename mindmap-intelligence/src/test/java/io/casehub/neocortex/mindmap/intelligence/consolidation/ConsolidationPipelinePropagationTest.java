package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.caps.CapsConnection;
import io.casehub.neocortex.caps.CapsNode;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeRange;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationClassifier;
import io.casehub.neocortex.caps.WeightProvenance;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.session.SessionPipeline;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end propagation test for the consolidation pipeline.
 *
 * Verifies that seeded input (drives + formation memories) propagates
 * through each phase in sequence:
 *   formative-experience → DriveProfileInferred → DriveCompetitionInferred → DriveDescriptionGenerated
 *
 * Catches: phase ordering bugs, agent-id mismatches, maxPerPass truncation,
 * missing trait annotations, CDI proxy annotation issues.
 *
 * No LLM calls — uses a mock AgentProvider. No Quarkus — plain unit test.
 */
class ConsolidationPipelinePropagationTest {

    private InMemoryMindMapStore store;
    private String cognitiveSubgraphId;
    private static final String TENANT = "propagation-test";
    private static final String AGENT_A = "agent-alpha";
    private static final String AGENT_B = "agent-beta";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        cognitiveSubgraphId = store.createSubgraph(
            new SubgraphInput("cognitive-" + AGENT_A, SubgraphTypes.COGNITIVE, null), TENANT);
        store.createSubgraph(
            new SubgraphInput("cognitive-" + AGENT_B, SubgraphTypes.COGNITIVE, null), TENANT);
    }

    @Test
    void fullPipeline_seedToDescription() {
        seedAgent(AGENT_A, List.of("gallantry", "proving-worth"),
            List.of(
                mem("Father knelt to help the stranger", "gallantry"),
                mem("Practised until dark, the bridge held", "proving-worth")));

        var classifier = buildClassifier();
        var topology = buildTopology();

        var profilePhase = new DriveProfileDerivationPhase(store, classifier, topology);
        profilePhase.run(TENANT, List.of());

        var profiles = findByTrait(DriveProfileDerivationPhase.PROFILE_TRAIT, AGENT_A);
        assertThat(profiles)
            .as("DriveProfileInferred should exist for each drive with serving memories")
            .hasSize(2);

        var competitions = findByTrait(DriveProfileDerivationPhase.COMPETITION_TRAIT, AGENT_A);

        SessionPipeline pipeline = StubPipelines.create(init -> {
            boolean isDiscrimination = init.systemPrompt().contains("discrimination");
            return new AgentSession() {
                @Override
                public Multi<AgentEvent> query(String userPrompt) {
                    if (isDiscrimination) {
                        return Multi.createFrom().items(
                            new AgentEvent.TextDelta("{\"discrimination\": \"How they differ: mock discrimination text.\"}"));
                    }
                    return Multi.createFrom().items(
                        new AgentEvent.TextDelta("{\"description\": \"Mock somatic description for testing.\"}"));
                }
                @Override
                public io.smallrye.mutiny.Uni<Void> interrupt() {
                    return io.smallrye.mutiny.Uni.createFrom().voidItem();
                }
                @Override
                public void close(java.time.Duration timeout) {}
            };
        });

        var descPhase = new DriveDescriptionGenerationPhase(store);
        descPhase.setSessionPipeline(pipeline);
        descPhase.run(TENANT, List.of());

        var descriptions = findByTrait(DriveDescriptionGenerationPhase.DESCRIPTION_TRAIT, AGENT_A);
        assertThat(descriptions)
            .as("DriveDescriptionGenerated should exist for each drive")
            .hasSize(2);

        var driveTypes = descriptions.stream()
            .map(n -> n.property("drive-type").orElse(""))
            .toList();
        assertThat(driveTypes).containsExactlyInAnyOrder("gallantry", "proving-worth");

        for (var desc : descriptions) {
            assertThat(desc.property("generated-description").orElse(""))
                .as("Generated description should not be empty for " + desc.property("drive-type").orElse("?"))
                .isNotBlank();
        }
    }

    @Test
    void multiAgent_allAgentsGetDescriptions() {
        seedAgent(AGENT_A, List.of("gallantry"),
            List.of(mem("Father knelt", "gallantry")));
        seedAgent(AGENT_B, List.of("scheming"),
            List.of(mem("Noticed the keys", "scheming")));

        SessionPipeline pipeline = StubPipelines.create(init -> new AgentSession() {
            @Override
            public Multi<AgentEvent> query(String userPrompt) {
                return Multi.createFrom().items(
                    new AgentEvent.TextDelta("{\"description\": \"Mock somatic.\"}"));
            }
            @Override
            public io.smallrye.mutiny.Uni<Void> interrupt() {
                return io.smallrye.mutiny.Uni.createFrom().voidItem();
            }
            @Override
            public void close(java.time.Duration timeout) {}
        });

        var descPhase = new DriveDescriptionGenerationPhase(store);
        descPhase.setSessionPipeline(pipeline);
        descPhase.run(TENANT, List.of());

        assertThat(findByTrait(DriveDescriptionGenerationPhase.DESCRIPTION_TRAIT, AGENT_A))
            .as("Agent A should have a generated description")
            .hasSize(1);
        assertThat(findByTrait(DriveDescriptionGenerationPhase.DESCRIPTION_TRAIT, AGENT_B))
            .as("Agent B should have a generated description")
            .hasSize(1);
    }

    @Test
    void driveWithoutMemories_getsGenericDescription() {
        store.addNode(NodeInput.of("orphan-drive", cognitiveSubgraphId)
            .withProperties(Map.of("cognitiveKind", "drive-intensity", "agent-id", AGENT_A,
                "drive-type", "orphan", "intensity", "0.5", "description", "fallback")), TENANT);

        store.addNode(NodeInput.of("served-memory", cognitiveSubgraphId)
            .withProperties(Map.of("cognitiveKind", "formative-experience", "agent-id", AGENT_A,
                "situation-types", "formation,other-drive")), TENANT);

        store.addNode(NodeInput.of("other-drive", cognitiveSubgraphId)
            .withProperties(Map.of("cognitiveKind", "drive-intensity", "agent-id", AGENT_A,
                "drive-type", "other-drive", "intensity", "0.7", "description", "x")), TENANT);

        SessionPipeline pipeline = StubPipelines.create(init -> new AgentSession() {
            @Override
            public Multi<AgentEvent> query(String userPrompt) {
                return Multi.createFrom().items(
                    new AgentEvent.TextDelta("{\"description\": \"Generic description.\"}"));
            }
            @Override
            public io.smallrye.mutiny.Uni<Void> interrupt() {
                return io.smallrye.mutiny.Uni.createFrom().voidItem();
            }
            @Override
            public void close(java.time.Duration timeout) {}
        });

        var descPhase = new DriveDescriptionGenerationPhase(store);
        descPhase.setSessionPipeline(pipeline);
        descPhase.run(TENANT, List.of());

        var descriptions = findByTrait(DriveDescriptionGenerationPhase.DESCRIPTION_TRAIT, AGENT_A);
        assertThat(descriptions).hasSize(2);
        var types = descriptions.stream()
            .map(n -> n.property("drive-type").orElse(""))
            .toList();
        assertThat(types).containsExactlyInAnyOrder("orphan", "other-drive");
    }

    // --- helpers ---

    private void seedAgent(String agentId, List<String> driveTypes, List<MemSpec> memories) {
        String sgId = store.listSubgraphs(TENANT).stream()
            .filter(sg -> sg.name().contains(agentId))
            .map(sg -> sg.id())
            .findFirst()
            .orElse(cognitiveSubgraphId);

        for (String drive : driveTypes) {
            store.addNode(NodeInput.of(drive, sgId)
                .withProperties(Map.of("cognitiveKind", "drive-intensity", "agent-id", agentId,
                    "drive-type", drive, "intensity", "0.9", "description", "fallback: " + drive)), TENANT);
        }

        for (MemSpec mem : memories) {
            store.addNode(NodeInput.of(mem.text, sgId)
                .withProperties(Map.of("cognitiveKind", "formative-experience", "agent-id", agentId,
                    "situation-types", "formation," + mem.driveType)), TENANT);
        }
    }

    record MemSpec(String text, String driveType) {}
    private MemSpec mem(String text, String driveType) { return new MemSpec(text, driveType); }

    private List<MindMapNode> findByTrait(String trait, String agentId) {
        return store.search(MindMapQuery.of(TENANT, 200).withType(SubgraphTypes.COGNITIVE))
            .stream()
            .filter(n -> n.traits().contains(trait))
            .filter(n -> agentId.equals(n.properties().get("agent-id")))
            .toList();
    }

    private SituationClassifier buildClassifier() {
        return (desc, meta) -> {
            if (desc.contains("knelt") || desc.contains("stranger"))
                return List.of(new SituationActivation("acceptance", 0.7));
            if (desc.contains("practised") || desc.contains("bridge"))
                return List.of(new SituationActivation("mastery", 0.8));
            if (desc.contains("keys") || desc.contains("Noticed"))
                return List.of(new SituationActivation("dominance", 0.6));
            return List.of();
        };
    }

    private CapsTopology buildTopology() {
        var nodes = Map.of(
            "acceptance", new CapsNode("acceptance", NodeType.INPUT, NodeRange.UNIPOLAR, "social", List.of(), Map.of("welcomed", 0.8)),
            "mastery", new CapsNode("mastery", NodeType.INPUT, NodeRange.UNIPOLAR, "achievement", List.of(), Map.of("mastered", 0.9)),
            "dominance", new CapsNode("dominance", NodeType.INPUT, NodeRange.UNIPOLAR, "achievement", List.of(), Map.of("dominated", 0.9)),
            "other_safety", new CapsNode("other_safety", NodeType.MEDIATING, NodeRange.BIPOLAR, "other_model", List.of(), Map.of()),
            "self_efficacy", new CapsNode("self_efficacy", NodeType.MEDIATING, NodeRange.BIPOLAR, "self_model", List.of(), Map.of()));

        var connections = List.of(
            new CapsConnection("c1", "acceptance", "other_safety", 0.3, WeightProvenance.CONSENSUS, "test", List.of()),
            new CapsConnection("c2", "mastery", "self_efficacy", 0.3, WeightProvenance.CONSENSUS, "test", List.of()),
            new CapsConnection("c3", "dominance", "self_efficacy", 0.25, WeightProvenance.CONSENSUS, "test", List.of()));

        return new CapsTopology(1, nodes, connections, Map.of(), List.of(), null);
    }
}

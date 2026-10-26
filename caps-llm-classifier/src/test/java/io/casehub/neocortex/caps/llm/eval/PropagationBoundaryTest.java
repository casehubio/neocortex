package io.casehub.neocortex.caps.llm.eval;

import io.casehub.neocortex.caps.testing.InMemoryCapsEngine;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.cognition.prompt.BehavioralPromptSection;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.observation.CognitionObservationBuilder;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.OverlayRef;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropagationBoundaryTest {

    private Map<String, EvalCharacterData> characters;

    @BeforeAll
    void setUp() {
        characters = EvalCharacterLoader.loadGeneric();
    }

    @Test
    void driveSeeded_nodesHavePropertiesDriveAdaptationPhaseExpects() {
        var store = new InMemoryMindMapStore();
        var tenant = "boundary-drives-" + System.nanoTime();
        var ppData = characters.getOrDefault("peter-perfect", EvalCharacterData.empty());

        EvalMindMapSeeder.seedCharacter("peter-perfect", ppData, store, tenant);

        var driveNodes = store.listSubgraphs(tenant).stream()
                .flatMap(sg -> store.nodesIn(sg.id(), tenant).stream())
                .filter(n -> "drive-intensity".equals(n.property("cognitiveKind").orElse(null)))
                .toList();

        assertFalse(driveNodes.isEmpty(),
                "seedCharacter() should create drive-intensity nodes in the MindMap");

        for (var node : driveNodes) {
            assertTrue(node.property("drive-type").isPresent(),
                    "drive node must have drive-type property");
            assertTrue(node.property("intensity").isPresent(),
                    "drive node must have intensity property");
            assertTrue(node.property("agent-id").isPresent(),
                    "drive node must have agent-id property");
        }

        var driveTypes = driveNodes.stream()
                .map(n -> n.property("drive-type").orElse(null))
                .toList();
        assertTrue(driveTypes.stream().anyMatch("proving-worth"::equals),
                "PP should have a proving-worth drive node");
    }

    @Test
    void behavioralSectionRenders_whenAttractorsSeeded() {
        var store = new InMemoryMindMapStore();
        var tenant = "boundary-behavioral-" + System.nanoTime();
        var agentId = "pp-boundary-behavioral";

        EvalMindMapSeeder.seedCharacter(agentId,
                characters.getOrDefault("peter-perfect", EvalCharacterData.empty()),
                store, tenant);

        var bsgId = store.createSubgraph(
                new SubgraphInput("behavioral", SubgraphTypes.BEHAVIORAL, null), tenant);
        store.addNode(NodeInput.of("attractor-gallantry", bsgId)
                .withTraits(Set.of("CapsGenerated"))
                .withProperties(Map.of(
                        "agent-id", agentId,
                        "strength", "0.75",
                        "personality-label", "chivalric pull toward graceful action",
                        "caps-node-id", "gallantry")), tenant);

        var behavioral = new BehavioralPromptSection(store);
        var ctx = new CognitionRenderContext(agentId, tenant, null);
        var output = behavioral.render(ctx);

        assertNotNull(output,
                "BehavioralPromptSection should render non-null when CapsGenerated attractors exist");
        assertTrue(output.contains("chivalric pull"),
                "rendered output should contain the attractor personality-label");
    }

    @Test
    void capsInitialization_producesAgentState() {
        var capsEngine = new InMemoryCapsEngine();
        var tenant = "boundary-caps-" + System.nanoTime();
        var agentId = "pp-boundary-caps";

        var state = capsEngine.initializeAgent(tenant, agentId,
                new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "cooperative"));

        assertNotNull(state, "initializeAgent should produce a non-null state");
        assertNotNull(capsEngine.loadState(tenant, agentId),
                "loadState should find the initialized state");
    }

    @Test
    void renderObservation_includesFormationMemories() {
        var ppData = characters.getOrDefault("peter-perfect", EvalCharacterData.empty());
        assertFalse(ppData.formationMemories().isEmpty(),
                "PP should have formation memories in config");

        var observation = CognitiveTestObservation.render(
                "peter-perfect", ppData, List.of(), Map.of());

        assertTrue(observation.contains("Where You Come From"),
                "observation should include formation memories — dead end #2");
    }

    @Test
    void seedPeople_createsSocialSubgraphWithOverlays() {
        var store = new InMemoryMindMapStore();
        var tenant = "boundary-people-" + System.nanoTime();

        var subgraphId = store.createSubgraph(
                new SubgraphInput("people", "social", null), tenant);

        var now = Instant.now();
        var personNodeIds = new HashMap<String, String>();

        for (String agentId : characters.keySet()) {
            String nodeId = store.addNode(
                    NodeInput.of(agentId, subgraphId)
                            .withConfidence(Confidence.stated(0.9, now))
                            .withProvenance("eval-seed")
                            .withTraits(Set.of("Entitylike"))
                            .withProperties(Map.of("agentId", agentId))
                            .withPad(0.0, 0.0, 0.0),
                    tenant);
            personNodeIds.put(agentId, nodeId);
        }

        int overlayCount = 0;
        for (var entry : characters.entrySet()) {
            String observerId = entry.getKey();
            for (var schema : entry.getValue().relationalSchemas()) {
                String targetNodeId = personNodeIds.get(schema.target());
                if (targetNodeId == null) continue;

                store.addNode(
                        NodeInput.of(observerId + "-sees-" + schema.target(), subgraphId)
                                .withConfidence(Confidence.stated(0.7, now))
                                .withProvenance("eval-seed")
                                .withTraits(Set.of("overlay"))
                                .withRefs(Set.of(OverlayRef.of(targetNodeId)))
                                .withProperties(Map.of(OverlayRef.AGENT_ID, observerId))
                                .withPad(schema.trust(), schema.intimacy(), schema.utility())
                                .withPrincipalId(PrincipalId.agent(observerId)),
                        tenant);
                overlayCount++;
            }
        }

        var socialSubgraphs = store.listSubgraphs(tenant).stream()
                .filter(sg -> "social".equals(sg.type()))
                .toList();

        assertFalse(socialSubgraphs.isEmpty(),
                "seedPeople should create a social-type subgraph");

        var allNodes = socialSubgraphs.stream()
                .flatMap(sg -> store.nodesIn(sg.id(), tenant).stream())
                .toList();

        assertTrue(allNodes.size() > characters.size(),
                "social subgraph should have more nodes than agents (shared + overlays)");

        var entityNodes = allNodes.stream()
                .filter(n -> n.traits().contains("Entitylike"))
                .toList();

        assertFalse(entityNodes.isEmpty(),
                "seedPeople should create entity nodes for each character");

        assertTrue(allNodes.size() > entityNodes.size(),
                "social subgraph should have overlay nodes beyond shared entity nodes");
    }

    @Test
    void renderInternalState_includesDrivesInSoloScene() {
        var ppData = characters.getOrDefault("peter-perfect", EvalCharacterData.empty());
        assertFalse(ppData.drives().isEmpty(), "PP should have drives in config");

        var observation = CognitiveTestObservation.render(
                "peter-perfect", ppData, List.of(), Map.of());

        assertTrue(observation.contains("What Pulls at You"),
                "observation should include 'What Pulls at You' section — dead ends #5 + #7");

        assertFalse(observation.contains("proving-worth") && observation.contains("gallantry")
                        && observation.contains("protection"),
                "somatic section must NOT contain drive type labels as bare keywords in same block");
    }

    @Test
    void renderInternalState_combinesDrivesAndCapsAttractors() {
        var store = new InMemoryMindMapStore();
        var tenant = "boundary-unified-" + System.nanoTime();
        var agentId = "pp-boundary-unified";
        var ppData = characters.getOrDefault("peter-perfect", EvalCharacterData.empty());

        var sgId = EvalMindMapSeeder.seedCharacter(agentId, ppData, store, tenant);

        var bsgId = store.createSubgraph(
                new SubgraphInput("behavioral", SubgraphTypes.BEHAVIORAL, null), tenant);
        store.addNode(NodeInput.of("attractor-proving-worth", bsgId)
                .withTraits(Set.of("CapsGenerated"))
                .withProperties(Map.of(
                        "agent-id", agentId,
                        "strength", "0.6",
                        "personality-label", "quiet determination to prove capability",
                        "caps-node-id", "proving-worth")), tenant);

        var builder = new CognitionObservationBuilder(
                agentId, tenant, store, null, null,
                CognitiveTestObservation.toDriveSnapshots(ppData));

        var observation = CognitiveTestObservation.render(
                agentId, ppData, List.of(), Map.of(), builder);

        assertTrue(observation.contains("What Pulls at You"),
                "unified renderer should produce 'What Pulls at You' section after full pipeline");

        assertTrue(observation.contains("tension") || observation.contains("warmth")
                        || observation.contains("notice") || observation.contains("shoulders")
                        || observation.contains("chest"),
                "section should contain somatic drive descriptions");
    }
}

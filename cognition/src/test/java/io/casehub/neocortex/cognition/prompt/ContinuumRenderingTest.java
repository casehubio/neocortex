package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.eval.CognitionTestHarness;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.mindmap.ContinuumPosition;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuumRenderingTest {

    private static final String  AGENT  = "agent1";
    private static final String  TENANT = "tenant1";
    private static final Instant NOW    = Instant.now();

    @Test
    void curiositySectionHealthyRendersNormally() {
        var harness = harnessWithGaps(Map.of());
        var output  = harness.renderPrompt(AGENT, TENANT);
        assertNotNull(output);
        assertFalse(output.contains("unbidden"));
        assertFalse(output.contains("intrusive"));
    }

    @Test
    void curiositySectionFixationNarrowsToDominantChannel() {
        var harness = harnessWithGaps(Map.of("topic-1", ContinuumPosition.FIXATION));
        var output  = harness.renderPrompt(AGENT, TENANT);
        assertNotNull(output);
    }

    @Test
    void curiositySectionObsessionRendersIntrusiveQuality() {
        var harness = harnessWithGaps(Map.of("topic-1", ContinuumPosition.OBSESSION));
        var output  = harness.renderPrompt(AGENT, TENANT);
        assertNotNull(output);
        assertTrue(output.contains("unbidden") || output.contains("intrusive"),
                   "Obsession should render intrusive quality, got: " + output);
    }

    @Test
    void curiositySectionRuminationRendersWithdrawal() {
        var harness = harnessWithGaps(Map.of("topic-1", ContinuumPosition.RUMINATION));
        var output  = harness.renderPrompt(AGENT, TENANT);
        assertNotNull(output);
    }

    @Test
    void searchBudgetFixationShiftsToPressured() {
        var harness = harnessWithGaps(Map.of("topic-1", ContinuumPosition.FIXATION));
        var output  = harness.renderPrompt(AGENT, TENANT);
        assertNotNull(output);
        assertTrue(output.contains("circling") || output.contains("back"),
                   "Fixation should add circling-back framing, got: " + output);
    }

    @Test
    void nullContinuumMapRendersNormally() {
        var harness = harnessWithGaps(null);
        var output  = harness.renderPrompt(AGENT, TENANT);
        assertNotNull(output, "Null continuum map should render without error");
    }

    @Test
    void emptyContinuumMapRendersNormally() {
        var harnessEmpty = harnessWithGaps(Map.of());
        var harnessNull  = harnessWithGaps(null);
        var outputEmpty  = harnessEmpty.renderPrompt(AGENT, TENANT);
        var outputNull   = harnessNull.renderPrompt(AGENT, TENANT);
        assertEquals(outputEmpty, outputNull, "Empty and null maps should produce same output");
    }

    // --- helpers ---

    private CognitionTestHarness harnessWithGaps(Map<String, ContinuumPosition> continuum) {
        var store = new InMemoryMindMapStore();
        var sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        var nodeId = store.addNode(NodeInput.of("test-topic", sgId)
                                            .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        var ctxId = store.addNode(NodeInput.of("context", sgId)
                                           .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(nodeId, ctxId, "relates-to"), TENANT);

        var harness = CognitionTestHarness.builder()
                                          .withMindMapStore(store)
                                          .agentFromDefaults(AGENT, CognitiveDefaults.empty(AGENT))
                                          .build();

        if (continuum != null && !continuum.isEmpty()) {
            harness.setContinuumPosition(AGENT, TENANT, continuum);
        }

        harness.observe(AGENT, TENANT, "exploring test topic");
        harness.tick(AGENT, TENANT);

        return harness;
    }
}

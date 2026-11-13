package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveTrace;
import io.casehub.neocortex.cognition.journal.EventRef;
import io.casehub.neocortex.cognitive.observability.DerivationObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DerivationJournalBridgeTest {

    private CognitiveTrace trace;
    private DerivationJournalBridge bridge;

    @BeforeEach
    void setUp() {
        trace = new CognitiveTrace();
        bridge = new DerivationJournalBridge(trace, "tenant1");
    }

    @Test
    void onPipelineStartProducesDerivationStarted() {
        bridge.onPipelineStart("agent1", "init",
                new DerivationObserver.PipelineInput("axes", List.of("term"), List.of("facet"), List.of("goal")));
        assertEquals(1, trace.size());
        var entry = trace.entriesOfType(CognitiveEntry.DerivationStarted.class).getFirst();
        assertEquals("agent1", entry.agentId());
        assertEquals("tenant1", entry.tenantId());
        assertEquals(EventRef.init(), entry.eventRef());
        assertEquals("axes", entry.axes());
    }

    @Test
    void onStageComputationProducesStageComputed() {
        bridge.onStageComputation("agent1", "init",
                new DerivationObserver.StageComputation("method", Map.of("k", 1.0),
                        List.of(new DerivationObserver.FacetEffect("f", 0.5, "field", 0.0, 0.5)),
                        Map.of("k", 1.5)));
        var entry = trace.entriesOfType(CognitiveEntry.StageComputed.class).getFirst();
        assertEquals("method", entry.method());
        assertEquals(1, entry.facetEffects().size());
        assertEquals(0.5, entry.facetEffects().getFirst().delta());
    }

    @Test
    void onDataFlowProducesDataFlowed() {
        bridge.onDataFlow("agent1", "init",
                new DerivationObserver.DataFlow("srcM", "srcF", "tgtM", "tgtF", 2.5));
        var entry = trace.entriesOfType(CognitiveEntry.DataFlowed.class).getFirst();
        assertEquals("srcM", entry.sourceMethod());
        assertEquals(2.5, entry.value());
    }

    @Test
    void onPipelineCompleteProducesDerivationCompleted() {
        bridge.onPipelineComplete("agent1", "init");
        assertEquals(1, trace.entriesOfType(CognitiveEntry.DerivationCompleted.class).size());
    }
}

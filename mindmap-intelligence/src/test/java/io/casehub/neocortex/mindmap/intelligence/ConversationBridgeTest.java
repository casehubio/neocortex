package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.consolidation.RetrievalAccessTracker;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationBridgeTest {

    private InMemoryMindMapStore store;
    private RetrievalAccessTracker tracker;
    private List<ExtractionRequested> firedEvents;
    private ConversationBridge bridge;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        tracker = new RetrievalAccessTracker();
        firedEvents = new ArrayList<>();
        bridge = new ConversationBridge(store, tracker, firedEvents::add);
    }

    @Test
    void process_blankText_returnsEmpty() {
        var result = bridge.process("", "t1", List.of(), null, null);
        assertThat(result).isEqualTo(SegmentationResult.EMPTY);
    }

    @Test
    void process_singleParagraph_createsOneNode() {
        var result = bridge.process(
            "Alice works on the knowledge graph project.",
            "t1", List.of(), null, null);
        assertThat(result.segmentCount()).isEqualTo(1);
        assertThat(result.createdNodeIds()).hasSize(1);

        MindMapNode node = store.getNode(result.createdNodeIds().getFirst(), "t1");
        assertThat(node).isNotNull();
        assertThat(node.property("body")).isPresent();
        assertThat(node.provenance()).isEqualTo("conversation-bridge");
    }

    @Test
    void process_multipleParagraphs_createsMultipleNodes() {
        var result = bridge.process(
            "Alice works on AI.\n\nBob studies biology.\n\nCarol builds bridges.",
            "t1", List.of(), null, null);
        assertThat(result.segmentCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.createdNodeIds()).hasSize(result.segmentCount());
    }

    @Test
    void process_recordsAccess() {
        bridge.process("Some text.", "t1", List.of(), null, null);
        var snapshot = tracker.swapAndReset();
        assertThat(snapshot.counts()).isNotEmpty();
    }

    @Test
    void process_firesExtractionEvent() {
        bridge.process("Some text.", "t1", List.of(), null, null);
        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.getFirst().tenantId()).isEqualTo("t1");
        assertThat(firedEvents.getFirst().segmentNodeIds()).isNotEmpty();
    }

    @Test
    void process_createsGeneralSubgraphIfMissing() {
        bridge.process("Some text.", "t1", List.of(), null, null);
        var subgraphs = store.listSubgraphs("t1");
        assertThat(subgraphs).anyMatch(sg -> SubgraphTypes.GENERAL.equals(sg.type()));
    }

    @Test
    void process_withPrincipalId_setsOnCreatedNodes() {
        PrincipalId pid    = PrincipalId.agent("agent-1");
        var         result = bridge.process("Some text.", "t1", List.of(), pid, null);
        assertThat(result.createdNodeIds()).isNotEmpty();
        MindMapNode node = store.getNode(result.createdNodeIds().getFirst(), "t1");
        assertThat(node.principalId()).isEqualTo(pid);
    }

    @Test
    void process_nullConfidence_defaultsToStated() {
        var         result = bridge.process("Some text.", "t1", List.of(), null, null);
        MindMapNode node   = store.getNode(result.createdNodeIds().getFirst(), "t1");
        assertThat(node.confidence().origin()).isEqualTo(ConfidenceOrigin.STATED);
        assertThat(node.confidence().value()).isEqualTo(1.0);
    }

    @Test
    void process_inferredConfidence_createsNodesAt07() {
        var result = bridge.process("Some text.", "t1", List.of(), null,
                                    ConfidenceOrigin.INFERRED);
        MindMapNode node = store.getNode(result.createdNodeIds().getFirst(), "t1");
        assertThat(node.confidence().origin()).isEqualTo(ConfidenceOrigin.INFERRED);
        assertThat(node.confidence().value()).isEqualTo(0.7);
    }

    @Test
    void process_firesExtractionEventWithPrincipalId() {
        PrincipalId pid = PrincipalId.agent("agent-1");
        bridge.process("Some text.", "t1", List.of(), pid, null);
        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.getFirst().principalId()).isEqualTo(pid);
    }


}

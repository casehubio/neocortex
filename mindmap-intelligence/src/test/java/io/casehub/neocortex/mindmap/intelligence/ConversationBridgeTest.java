package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.consolidation.RetrievalAccessTracker;
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
        var result = bridge.process("", "t1", List.of(), null);
        assertThat(result).isEqualTo(SegmentationResult.EMPTY);
    }

    @Test
    void process_singleParagraph_createsOneNode() {
        var result = bridge.process(
            "Alice works on the knowledge graph project.",
            "t1", List.of(), null);
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
            "t1", List.of(), null);
        assertThat(result.segmentCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.createdNodeIds()).hasSize(result.segmentCount());
    }

    @Test
    void process_recordsAccess() {
        bridge.process("Some text.", "t1", List.of(), null);
        var snapshot = tracker.swapAndReset();
        assertThat(snapshot.counts()).isNotEmpty();
    }

    @Test
    void process_firesExtractionEvent() {
        bridge.process("Some text.", "t1", List.of(), null);
        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.getFirst().tenantId()).isEqualTo("t1");
        assertThat(firedEvents.getFirst().segmentNodeIds()).isNotEmpty();
    }

    @Test
    void process_createsGeneralSubgraphIfMissing() {
        bridge.process("Some text.", "t1", List.of(), null);
        var subgraphs = store.listSubgraphs("t1");
        assertThat(subgraphs).anyMatch(sg -> SubgraphTypes.GENERAL.equals(sg.type()));
    }
}

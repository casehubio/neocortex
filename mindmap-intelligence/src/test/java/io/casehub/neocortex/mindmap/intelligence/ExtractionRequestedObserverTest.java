package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.consolidation.RetrievalAccessTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ExtractionRequestedObserverTest {

    private InMemoryMindMapStore store;
    private RetrievalAccessTracker tracker;
    private String subgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        tracker = new RetrievalAccessTracker();
        subgraphId = store.createSubgraph(
            new SubgraphInput("General", SubgraphTypes.GENERAL, null), "t1");
    }

    @Test
    void onEvent_supersedesSegmentNodes() {
        String segmentId = store.addNode(NodeInput.of("segment", subgraphId), "t1");

        var extractor = stubExtractor(new ExtractionResult(
            List.of(new ExtractedEntity("entity-1", "Alice", true, "person", Map.of())),
            List.of(), List.of(), List.of("Alice")));
        var observer = new ExtractionRequestedObserver(extractor, store, tracker);

        observer.onExtractionRequested(
            new ExtractionRequested("text", "t1", List.of(), List.of(segmentId)));

        var status = store.getSupersessionStatus(segmentId, "t1");
        assertThat(status.superseded()).isTrue();
    }

    @Test
    void onEvent_noEntities_segmentsPersist() {
        String segmentId = store.addNode(NodeInput.of("segment", subgraphId), "t1");

        var extractor = stubExtractor(ExtractionResult.EMPTY);
        var observer = new ExtractionRequestedObserver(extractor, store, tracker);

        observer.onExtractionRequested(
            new ExtractionRequested("text", "t1", List.of(), List.of(segmentId)));

        var status = store.getSupersessionStatus(segmentId, "t1");
        assertThat(status.superseded()).isFalse();
    }

    @Test
    void onEvent_recordsAccessForEntities() {
        var extractor = stubExtractor(new ExtractionResult(
            List.of(new ExtractedEntity("e1", "Alice", true, "person", Map.of()),
                    new ExtractedEntity("e2", "Bob", false, "person", Map.of())),
            List.of(), List.of(), List.of("Alice", "Bob")));
        var observer = new ExtractionRequestedObserver(extractor, store, tracker);

        observer.onExtractionRequested(
            new ExtractionRequested("text", "t1", List.of(), List.of()));

        var snapshot = tracker.swapAndReset();
        assertThat(snapshot.counts()).containsKeys("e1", "e2");
    }

    private MindMapExtractor stubExtractor(ExtractionResult result) {
        return new MindMapExtractor(store, null) {
            @Override
            public ExtractionResult extract(String text, String tenantId,
                                             List<String> recentEntityNames) {
                return result;
            }
        };
    }
}

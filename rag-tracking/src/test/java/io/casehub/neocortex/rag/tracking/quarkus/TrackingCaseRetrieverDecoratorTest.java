package io.casehub.neocortex.rag.tracking.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievalRecorded;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.testing.InMemoryRetrievalTracker;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TrackingCaseRetrieverDecoratorTest {

    @Test
    void delegatesToCoreTracking() {
        CorpusRef corpus = new CorpusRef("tenant-1", "kb");
        CaseRetriever delegate = (q, c, m, f) -> List.of(new RetrievedChunk("text", "doc-1", 0.7, Map.of()));
        var tracker = new InMemoryRetrievalTracker();
        var event = new AtomicReference<RetrievalRecorded>();

        var decorator = new TrackingCaseRetrieverDecorator(delegate, tracker, event::set);
        List<RetrievedChunk> chunks = decorator.retrieve(RetrievalQuery.of("q"), corpus, 5, null);

        assertThat(tracker.findRecords(corpus, Instant.EPOCH, Instant.MAX)).hasSize(1);
        assertThat(chunks).singleElement().satisfies(c -> assertThat(c.metadata()).containsKey("_trackingId"));
        assertThat(event.get()).isNotNull();
    }
}

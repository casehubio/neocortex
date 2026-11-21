package io.casehub.neocortex.rag.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievalTracker;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.testing.RetrievalTrackerContractTest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteRetrievalTrackerTest extends RetrievalTrackerContractTest {

    private final SqliteRetrievalTracker store =
        new SqliteRetrievalTracker(":memory:", 1, 5000, new ObjectMapper());

    @Override
    protected RetrievalTracker tracker() {
        return store;
    }

    @BeforeEach
    void reset() {
        store.clearAll();
    }

    @AfterEach
    void closeStore() {
        store.close();
    }

    @Test
    void twoTrackersOnSameFile_shareData(@TempDir Path dir) {
        String path = dir.resolve("tracking.db").toString();
        CorpusRef corpus = new CorpusRef("t1", "kb");
        String id;
        try (var first = new SqliteRetrievalTracker(path, 2, 5000, new ObjectMapper())) {
            id = first.record(RetrievalQuery.of("q"), corpus,
                List.of(new RetrievedChunk("text", "doc-1", 0.5, Map.of())), 5);
        }
        try (var second = new SqliteRetrievalTracker(path, 2, 5000, new ObjectMapper())) {
            assertThat(second.findRecords(corpus, Instant.EPOCH, Instant.MAX))
                .singleElement()
                .satisfies(r -> assertThat(r.retrievalId()).isEqualTo(id));
        }
    }

    @Test
    void close_twice_isSafe() {
        var tracker = new SqliteRetrievalTracker(":memory:", 1, 5000, new ObjectMapper());
        tracker.close();
        assertThatCode(tracker::close).doesNotThrowAnyException();
    }
}

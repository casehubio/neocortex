package io.casehub.neocortex.mindmap.intelligence.consolidation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RetrievalAccessTrackerTest {

    @Test
    void recordAccess_incrementsCount() {
        var tracker = new RetrievalAccessTracker();
        tracker.recordAccess("node-1");
        tracker.recordAccess("node-1");
        tracker.recordAccess("node-2");

        var snapshot = tracker.swapAndReset();
        assertThat(snapshot.counts()).containsEntry("node-1", 2L);
        assertThat(snapshot.counts()).containsEntry("node-2", 1L);
    }

    @Test
    void swapAndReset_clearsState() {
        var tracker = new RetrievalAccessTracker();
        tracker.recordAccess("node-1");

        var first = tracker.swapAndReset();
        assertThat(first.counts()).containsEntry("node-1", 1L);

        var second = tracker.swapAndReset();
        assertThat(second.counts()).isEmpty();
    }

    @Test
    void swapAndReset_capturesLastAccessTimes() {
        var tracker = new RetrievalAccessTracker();
        tracker.recordAccess("node-1");

        var snapshot = tracker.swapAndReset();
        assertThat(snapshot.lastAccessTimes()).containsKey("node-1");
        assertThat(snapshot.lastAccessTimes().get("node-1")).isNotNull();
    }

    @Test
    void recordAccess_afterSwap_writesToNewMap() {
        var tracker = new RetrievalAccessTracker();
        tracker.recordAccess("node-1");

        tracker.swapAndReset();
        tracker.recordAccess("node-2");

        var snapshot = tracker.swapAndReset();
        assertThat(snapshot.counts()).doesNotContainKey("node-1");
        assertThat(snapshot.counts()).containsEntry("node-2", 1L);
    }
}

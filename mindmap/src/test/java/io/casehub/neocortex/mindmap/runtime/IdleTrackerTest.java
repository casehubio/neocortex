package io.casehub.neocortex.mindmap.runtime;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class IdleTrackerTest {

    @Test
    void isIdle_noWrites_returnsTrue() {
        var tracker = new IdleTracker();
        assertThat(tracker.isIdle(Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void isIdle_recentWrite_returnsFalse() {
        var tracker = new IdleTracker();
        tracker.recordWrite();
        assertThat(tracker.isIdle(Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void isIdle_oldWrite_returnsTrue() {
        var tracker = new IdleTracker();
        tracker.recordWriteAt(Instant.now().minus(Duration.ofMinutes(5)));
        assertThat(tracker.isIdle(Duration.ofMinutes(1))).isTrue();
    }
}

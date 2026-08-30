package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporalEntryTest {

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T11:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-01T12:00:00Z");
    private static final Confidence CONF = new Confidence(ConfidenceOrigin.STATED, 0.9, T1);

    @Test
    void compareTo_sortsChronologically() {
        var early = new TemporalEntry(T1, new TemporalSource.FromMemory(null), "t1", CONF);
        var middle = new TemporalEntry(T2, new TemporalSource.FromMemory(null), "t1", CONF);
        var late = new TemporalEntry(T3, new TemporalSource.FromMemory(null), "t1", CONF);

        var sorted = List.of(late, early, middle).stream().sorted().toList();
        assertThat(sorted).containsExactly(early, middle, late);
    }

    @Test
    void nullTimestamp_throws() {
        assertThatThrownBy(() -> new TemporalEntry(null, new TemporalSource.FromMemory(null), "t1", CONF))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("timestamp");
    }

    @Test
    void nullSource_throws() {
        assertThatThrownBy(() -> new TemporalEntry(T1, null, "t1", CONF))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("source");
    }

    @Test
    void nullTenantId_throws() {
        assertThatThrownBy(() -> new TemporalEntry(T1, new TemporalSource.FromMemory(null), null, CONF))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    void nullConfidence_allowed() {
        var entry = new TemporalEntry(T1, new TemporalSource.FromMemory(null), "t1", null);
        assertThat(entry.confidence()).isNull();
    }
}

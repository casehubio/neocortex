package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;

import java.time.Instant;
import java.util.Objects;

/**
 * A single temporal event from any cognitive store. This is a derived view —
 * the source data lives in the originating store (MindMap, Memory, or CBR).
 * TemporalEntry carries the full source object via {@link TemporalSource}
 * for zero-information-loss access.
 *
 * <p>Natural ordering is chronological (oldest first). Confidence is nullable —
 * null means "no confidence assessment."
 */
public record TemporalEntry(
    Instant timestamp,
    TemporalSource source,
    String tenantId,
    Confidence confidence
) implements Comparable<TemporalEntry> {

    public TemporalEntry {
        Objects.requireNonNull(timestamp, "timestamp required");
        Objects.requireNonNull(source, "source required");
        Objects.requireNonNull(tenantId, "tenantId required");
    }

    @Override
    public int compareTo(TemporalEntry other) {
        return this.timestamp.compareTo(other.timestamp);
    }
}

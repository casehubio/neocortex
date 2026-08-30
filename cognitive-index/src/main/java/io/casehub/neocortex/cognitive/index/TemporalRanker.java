package io.casehub.neocortex.cognitive.index;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Composable scoring function for re-ordering {@link TemporalEntry} results.
 * Orthogonal to {@link TemporalIndex} — the index produces chronological
 * data, the ranker re-orders it by salience or any other criterion.
 *
 * <p>This is not a store or a persistent component. It is a pure function
 * over derived view data from {@link TemporalIndex}.
 */
@FunctionalInterface
public interface TemporalRanker {

    double score(TemporalEntry entry, Instant now);

    default List<TemporalEntry> rank(List<TemporalEntry> entries, Instant now) {
        return entries.stream()
            .sorted(Comparator.comparingDouble((TemporalEntry e) -> score(e, now)).reversed())
            .toList();
    }

    static TemporalRanker recency() {
        return (entry, now) -> {
            long seconds = Duration.between(entry.timestamp(), now).abs().getSeconds();
            return 1.0 / (1.0 + seconds);
        };
    }
}

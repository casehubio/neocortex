package io.casehub.neocortex.mindmap;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Derives {@code validFrom} and {@code validUntil} from lifecycle properties.
 * Uses the first-existence and last-existence label mappings per subgraph type
 * to determine temporal validity bounds.
 */
public final class LifecycleResolver {

    public record TemporalBounds(Instant validFrom, Instant validUntil) {}

    private LifecycleResolver() {}

    public static TemporalBounds resolve(Map<String, String> properties, String subgraphType) {
        Map<String, String> events = LifecycleEvents.extract(properties);
        if (events.isEmpty()) {
            return new TemporalBounds(null, null);
        }

        Instant validFrom = resolveFirst(events, LifecycleEvents.firstExistenceLabels(subgraphType));
        Instant validUntil = resolveLast(events, LifecycleEvents.lastExistenceLabels(subgraphType));

        return new TemporalBounds(validFrom, validUntil);
    }

    private static Instant resolveFirst(Map<String, String> events, List<String> labels) {
        Instant earliest = null;
        for (String label : labels) {
            String value = events.get(label);
            if (value != null) {
                Instant start = LifecycleDate.parse(value).toStartInstant();
                if (earliest == null || start.isBefore(earliest)) {
                    earliest = start;
                }
            }
        }
        return earliest;
    }

    private static Instant resolveLast(Map<String, String> events, List<String> labels) {
        Instant latest = null;
        for (String label : labels) {
            String value = events.get(label);
            if (value != null) {
                Instant end = LifecycleDate.parse(value).toEndInstant();
                if (latest == null || end.isAfter(latest)) {
                    latest = end;
                }
            }
        }
        return latest;
    }
}

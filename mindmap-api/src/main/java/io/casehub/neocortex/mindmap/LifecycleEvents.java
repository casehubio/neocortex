package io.casehub.neocortex.mindmap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convention constants and helpers for the {@code lifecycle.*} property prefix
 * on MindMap nodes. Each lifecycle event is a dated milestone stored as a
 * node property with key {@code lifecycle.<label>} and an ISO 8601 partial
 * date value (parsed by {@link LifecycleDate}).
 */
public final class LifecycleEvents {

    public static final String PREFIX = "lifecycle.";

    private static final Map<String, List<String>> STANDARD_LABELS = Map.of(
            SubgraphTypes.PERSON, List.of("born", "died"),
            SubgraphTypes.PROJECT, List.of("conceived", "started", "completed",
                    "first-exhibited", "published", "sold", "destroyed", "lost"),
            SubgraphTypes.PLACE, List.of("founded", "built", "renamed",
                    "demolished", "opened-as-museum"),
            SubgraphTypes.ORGANISATION, List.of("founded", "merged", "dissolved"),
            SubgraphTypes.ACTIVITY, List.of("occurred", "started", "ended"),
            SubgraphTypes.CONCEPT, List.of("coined", "popularised")
    );

    private static final Map<String, List<String>> FIRST_EXISTENCE = Map.of(
            SubgraphTypes.PERSON, List.of("born"),
            SubgraphTypes.PROJECT, List.of("conceived", "started", "completed"),
            SubgraphTypes.PLACE, List.of("founded", "built"),
            SubgraphTypes.ORGANISATION, List.of("founded"),
            SubgraphTypes.ACTIVITY, List.of("occurred", "started"),
            SubgraphTypes.CONCEPT, List.of("coined")
    );

    private static final Map<String, List<String>> LAST_EXISTENCE = Map.of(
            SubgraphTypes.PERSON, List.of("died"),
            SubgraphTypes.PROJECT, List.of("destroyed", "lost"),
            SubgraphTypes.PLACE, List.of("demolished"),
            SubgraphTypes.ORGANISATION, List.of("dissolved"),
            SubgraphTypes.ACTIVITY, List.of("ended"),
            SubgraphTypes.CONCEPT, List.of()
    );

    private LifecycleEvents() {}

    public static String key(String label) {
        return PREFIX + label;
    }

    public static List<String> standardLabels(String subgraphType) {
        return STANDARD_LABELS.getOrDefault(subgraphType, List.of());
    }

    public static List<String> firstExistenceLabels(String subgraphType) {
        return FIRST_EXISTENCE.getOrDefault(subgraphType, List.of());
    }

    public static List<String> lastExistenceLabels(String subgraphType) {
        return LAST_EXISTENCE.getOrDefault(subgraphType, List.of());
    }

    public static Map<String, String> extract(Map<String, String> properties) {
        var result = new LinkedHashMap<String, String>();
        for (var entry : properties.entrySet()) {
            if (entry.getKey().startsWith(PREFIX)) {
                result.put(entry.getKey().substring(PREFIX.length()), entry.getValue());
            }
        }
        return Map.copyOf(result);
    }

    public static Map<String, String> withEvent(Map<String, String> properties, String label, String dateValue) {
        var result = new LinkedHashMap<>(properties);
        result.put(key(label), dateValue);
        return Map.copyOf(result);
    }
}

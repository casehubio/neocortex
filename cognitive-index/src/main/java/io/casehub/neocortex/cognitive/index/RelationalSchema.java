package io.casehub.neocortex.cognitive.index;

import java.util.HashMap;
import java.util.Map;

public record RelationalSchema(
        String targetId,
        String role,
        double trust,
        double intimacy,
        double utility,
        String description
) {
    public static final String PREFIX = "rel.";

    public RelationalSchema {
        if (trust < -1.0 || trust > 1.0)
            throw new IllegalArgumentException("trust must be in [-1,1], got " + trust);
        if (intimacy < 0.0 || intimacy > 1.0)
            throw new IllegalArgumentException("intimacy must be in [0,1], got " + intimacy);
        if (utility < 0.0 || utility > 1.0)
            throw new IllegalArgumentException("utility must be in [0,1], got " + utility);
    }

    public Map<String, String> toProperties() {
        var props = new HashMap<String, String>();
        props.put(PREFIX + "target-id", targetId);
        props.put(PREFIX + "role", role);
        props.put(PREFIX + "trust", String.valueOf(trust));
        props.put(PREFIX + "intimacy", String.valueOf(intimacy));
        props.put(PREFIX + "utility", String.valueOf(utility));
        if (description != null) props.put(PREFIX + "description", description);
        return Map.copyOf(props);
    }

    public static RelationalSchema fromProperties(Map<String, String> props) {
        var targetId = props.get(PREFIX + "target-id");
        if (targetId == null) return null;
        return new RelationalSchema(
                targetId,
                props.getOrDefault(PREFIX + "role", "stranger"),
                clamp(parseDouble(props.get(PREFIX + "trust"), 0.0), -1.0, 1.0),
                clamp(parseDouble(props.get(PREFIX + "intimacy"), 0.0), 0.0, 1.0),
                clamp(parseDouble(props.get(PREFIX + "utility"), 0.0), 0.0, 1.0),
                props.get(PREFIX + "description")
        );
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

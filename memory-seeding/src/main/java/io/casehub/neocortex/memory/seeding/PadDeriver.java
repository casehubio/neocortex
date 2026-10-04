package io.casehub.neocortex.memory.seeding;

public final class PadDeriver {

    private PadDeriver() {}

    public record PadValues(double pleasure, double arousal, double dominance) {}

    public static PadValues derive(String nodeId, double intensity) {
        String category = categorize(nodeId);
        return switch (category) {
            case "threat"       -> new PadValues(-intensity * 0.8, intensity * 0.7, -intensity * 0.5);
            case "relationship" -> new PadValues(intensity * 0.6, intensity * 0.3, intensity * 0.3);
            case "achievement"  -> new PadValues(intensity * 0.7, intensity * 0.5, intensity * 0.6);
            case "social"       -> new PadValues(intensity * 0.5, intensity * 0.4, intensity * 0.2);
            case "resource"     -> new PadValues(-intensity * 0.6, intensity * 0.5, -intensity * 0.4);
            case "autonomy"     -> new PadValues(intensity * 0.4, intensity * 0.3, intensity * 0.7);
            case "consequence"  -> new PadValues(-intensity * 0.5, intensity * 0.6, -intensity * 0.3);
            default             -> new PadValues(0.0, intensity * 0.3, 0.0);
        };
    }

    private static String categorize(String nodeId) {
        if (nodeId.contains("threat") || nodeId.contains("rejection") || nodeId.contains("neglect")
            || nodeId.contains("abuse") || nodeId.contains("hostile") || nodeId.contains("FFFS"))
            return "threat";
        if (nodeId.contains("secure") || nodeId.contains("attachment") || nodeId.contains("care")
            || nodeId.contains("trust") || nodeId.contains("proximity"))
            return "relationship";
        if (nodeId.contains("achievement") || nodeId.contains("reward") || nodeId.contains("success")
            || nodeId.contains("competence") || nodeId.contains("mastery"))
            return "achievement";
        if (nodeId.contains("social") || nodeId.contains("model") || nodeId.contains("peer")
            || nodeId.contains("observation"))
            return "social";
        if (nodeId.contains("scarcity") || nodeId.contains("deprivation") || nodeId.contains("loss")
            || nodeId.contains("resource"))
            return "resource";
        if (nodeId.contains("autonomy") || nodeId.contains("control") || nodeId.contains("agency")
            || nodeId.contains("self_direct"))
            return "autonomy";
        if (nodeId.contains("punishment") || nodeId.contains("consequence") || nodeId.contains("negative_outcome"))
            return "consequence";
        return "unknown";
    }
}

package io.casehub.neocortex.mindmap;

public enum GoalTier {
    THEMATIC, STRATEGIC, TACTICAL;

    public static GoalTier fromHorizon(String horizon) {
        if (horizon == null) return TACTICAL;
        return switch (horizon) {
            case "aspirational" -> THEMATIC;
            case "medium", "long" -> STRATEGIC;
            case "immediate", "short" -> TACTICAL;
            default -> TACTICAL;
        };
    }
}

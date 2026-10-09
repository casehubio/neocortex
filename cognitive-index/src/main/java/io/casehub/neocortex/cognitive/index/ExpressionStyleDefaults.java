package io.casehub.neocortex.cognitive.index;

import java.util.Map;

public record ExpressionStyleDefaults(Map<String, String> driveExpressions) {

    public ExpressionStyleDefaults {
        driveExpressions = driveExpressions != null ? Map.copyOf(driveExpressions) : Map.of();
    }

    public String expressionFor(String driveCategory) {
        return driveExpressions.getOrDefault(driveCategory, null);
    }

    public static ExpressionStyleDefaults empty() {
        return new ExpressionStyleDefaults(Map.of());
    }
}

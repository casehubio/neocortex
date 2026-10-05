package io.casehub.neocortex.cognition.appraisal;

import java.util.Map;
import java.util.Objects;

public record SecResult(
        String checkName,
        Map<String, Double> dimensions) {
    public SecResult {
        Objects.requireNonNull(checkName, "checkName");
        dimensions = dimensions != null ? Map.copyOf(dimensions) : Map.of();
    }

    public double dimension(String name) {
        return dimensions.getOrDefault(name, 0.0);
    }

    public static SecResult of(String checkName, String dim1, double val1) {
        return new SecResult(checkName, Map.of(dim1, val1));
    }

    public static SecResult of(String checkName, String dim1, double val1,
                               String dim2, double val2) {
        return new SecResult(checkName, Map.of(dim1, val1, dim2, val2));
    }

    public static SecResult of(String checkName, String dim1, double val1,
                               String dim2, double val2, String dim3, double val3) {
        return new SecResult(checkName, Map.of(dim1, val1, dim2, val2, dim3, val3));
    }
}

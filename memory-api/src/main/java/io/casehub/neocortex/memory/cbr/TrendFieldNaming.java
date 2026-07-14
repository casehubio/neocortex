package io.casehub.neocortex.memory.cbr;

import java.util.Locale;

public final class TrendFieldNaming {

    private TrendFieldNaming() {}

    public static String name(String timeSeriesName, TrendType type, String innerFieldName) {
        String typePart = type.name().toLowerCase(Locale.ROOT);
        if (type.isPerField()) {
            if (innerFieldName == null || innerFieldName.isBlank()) {
                throw new IllegalArgumentException(
                        "innerFieldName required for per-field TrendType " + type);
            }
            return timeSeriesName + "_" + typePart + "_" + innerFieldName;
        }
        return timeSeriesName + "_" + typePart;
    }
}

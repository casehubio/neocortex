package io.casehub.neocortex.memory.cbr;

import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;

public record TrendSpec(Set<TrendType> types, ChronoUnit timeUnit) {
    public TrendSpec {
        Objects.requireNonNull(types, "types required");
        if (types.isEmpty()) {
            throw new IllegalArgumentException("At least one TrendType required");
        }
        types = Set.copyOf(types);
        Objects.requireNonNull(timeUnit, "timeUnit required");
    }

    public TrendSpec(Set<TrendType> types) {
        this(types, ChronoUnit.HOURS);
    }
}

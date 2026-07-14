package io.casehub.neocortex.memory.cbr;

import java.util.Objects;

public record CbrAdaptationRecorded(AdaptationTrace trace) {
    public CbrAdaptationRecorded {
        Objects.requireNonNull(trace, "trace");
    }
}

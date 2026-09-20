package io.casehub.neocortex.memory.cbr;

import java.util.Objects;

public record CbrAdaptationRecorded(CbrAdaptationTrace trace) {
    public CbrAdaptationRecorded {
        Objects.requireNonNull(trace, "trace");
    }
}

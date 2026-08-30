package io.casehub.neocortex.memory.mood;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class MoodDecay {

    private MoodDecay() {}

    public static MoodState decay(MoodState current, MoodBaseline baseline,
                                   Duration elapsed, Duration timeConstant) {
        Objects.requireNonNull(current, "current required");
        Objects.requireNonNull(baseline, "baseline required");
        Objects.requireNonNull(elapsed, "elapsed required");
        Objects.requireNonNull(timeConstant, "timeConstant required");

        double elapsedMs = Math.max(0, elapsed.toMillis());
        double tauMs = timeConstant.toMillis();
        if (elapsedMs == 0 || tauMs == 0) return current;

        double factor = 1.0 - Math.exp(-elapsedMs / tauMs);

        return new MoodState(
                current.agentId(),
                current.tenantId(),
                null, decayAxis(current.pleasure(), baseline.pleasure(), factor),
                decayAxis(current.arousal(), baseline.arousal(), factor),
                decayAxis(current.dominance(), baseline.dominance(), factor),
                "decay",
                null,
                Map.of()
        );
    }

    private static double decayAxis(double current, double baseline, double factor) {
        return current + (baseline - current) * factor;
    }
}

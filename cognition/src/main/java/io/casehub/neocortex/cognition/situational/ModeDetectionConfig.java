package io.casehub.neocortex.cognition.situational;

import java.time.Duration;
import java.util.List;

public record ModeDetectionConfig(
        SituationalMode.Group group,
        List<SignalConfig> exogenous,
        List<SignalConfig> endogenous,
        SignalConfig.RelationalConfig relational,
        double thresholdOn,
        double thresholdOff,
        Duration fadeTau
) {
    public ModeDetectionConfig {
        if (thresholdOff >= thresholdOn) {
            throw new IllegalArgumentException(
                    "threshold_off (%s) must be less than threshold_on (%s)".formatted(thresholdOff, thresholdOn));
        }
    }
}

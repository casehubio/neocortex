package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import org.jspecify.annotations.Nullable;
import java.util.List;

public record EmotionRegulationResult(
    EmotionRegulationStrategy strategy,
    List<String> targetEmotions,
    List<String> targetDrives,
    double peakOriginalIntensity,
    double regulatedIntensity,
    double effectiveness,
    String narrative,
    @Nullable DriveAxis regulationAxis,
    @Nullable String environmentalDirective
) {
    // Gross 1998 — strategies 1-2 target the environment, 3-5 target internal state
    public boolean isEnvironmentalStrategy() {
        return strategy == EmotionRegulationStrategy.SITUATION_SELECTION
            || strategy == EmotionRegulationStrategy.SITUATION_MODIFICATION;
    }
}

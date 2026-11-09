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
        @Nullable String environmentalDirective,
        @Nullable String targetAgentId
) {
    public EmotionRegulationResult(EmotionRegulationStrategy strategy,
                                   List<String> targetEmotions,
                                   List<String> targetDrives,
                                   double peakOriginalIntensity,
                                   double regulatedIntensity,
                                   double effectiveness,
                                   String narrative,
                                   @Nullable DriveAxis regulationAxis,
                                   @Nullable String environmentalDirective) {
        this(strategy, targetEmotions, targetDrives, peakOriginalIntensity,
             regulatedIntensity, effectiveness, narrative, regulationAxis,
             environmentalDirective, null);
    }

    public boolean isEnvironmentalStrategy() {
        return strategy == EmotionRegulationStrategy.SITUATION_SELECTION
               || strategy == EmotionRegulationStrategy.SITUATION_MODIFICATION;
    }

    public boolean isSocialStrategy() {
        return strategy == EmotionRegulationStrategy.SOCIAL_SUPPORT_SEEKING
               || strategy == EmotionRegulationStrategy.SOCIAL_SHARING
               || strategy == EmotionRegulationStrategy.SOCIAL_WITHDRAWAL
               || strategy == EmotionRegulationStrategy.CO_REGULATION
               || strategy == EmotionRegulationStrategy.DELIBERATE_PROVOCATION;
    }

    public boolean isExtrinsicStrategy() {
        return strategy == EmotionRegulationStrategy.CO_REGULATION
               || strategy == EmotionRegulationStrategy.DELIBERATE_PROVOCATION;
    }
}

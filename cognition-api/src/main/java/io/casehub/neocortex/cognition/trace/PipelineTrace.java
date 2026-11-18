package io.casehub.neocortex.cognition.trace;

import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.ChannelWeightProfile;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.cognition.drive.DriveEvaluation;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record PipelineTrace(
        @Nullable AppraisalResult appraisalResult,
        Map<String, Double> concernSalience,
        Map<String, Double> capsInputActivations,
        @Nullable CapsSettlingSnapshot capsSettling,
        @Nullable EmotionRegulationResult regulationResult,
        @Nullable ChannelWeightProfile channelWeights,
        @Nullable HabituationState habituation,
        List<String> activatedDriveCategories,
        Map<String, Double> driveIntensities,
        List<DriveEvaluation> driveEvaluations,
        Map<String, String> promptSections,
        @Nullable SituationalPersonalitySnapshot situationalPersonality
) {
    public PipelineTrace {
        driveIntensities = driveIntensities != null ? Map.copyOf(driveIntensities) : Map.of();
        driveEvaluations = driveEvaluations != null ? List.copyOf(driveEvaluations) : List.of();
    }

    public PipelineTrace(
            @Nullable AppraisalResult appraisalResult,
            Map<String, Double> concernSalience,
            Map<String, Double> capsInputActivations,
            @Nullable CapsSettlingSnapshot capsSettling,
            @Nullable EmotionRegulationResult regulationResult,
            @Nullable ChannelWeightProfile channelWeights,
            @Nullable HabituationState habituation,
            List<String> activatedDriveCategories,
            Map<String, Double> driveIntensities,
            List<DriveEvaluation> driveEvaluations,
            Map<String, String> promptSections) {
        this(appraisalResult, concernSalience, capsInputActivations, capsSettling,
             regulationResult, channelWeights, habituation, activatedDriveCategories,
             driveIntensities, driveEvaluations, promptSections, null);
    }

    public record CapsSettlingSnapshot(
            Map<String, Double> activations,
            int iterations,
            String convergenceType,
            double saturationRatio,
            List<AttractorSnapshot> attractors
    ) {
        public CapsSettlingSnapshot {
            attractors = attractors != null ? List.copyOf(attractors) : List.of();
        }

        public CapsSettlingSnapshot(Map<String, Double> activations, int iterations,
                                    String convergenceType, double saturationRatio) {
            this(activations, iterations, convergenceType, saturationRatio, List.of());
        }
    }

    public record AttractorSnapshot(String category, String nodeId, double strength) {}

    public record SituationalPersonalitySnapshot(
            Map<String, Double> modeIntensities,
            @Nullable String affectiveWinner,
            @Nullable String socialWinner,
            Map<String, Double> facetDisplacements,
            int publishedCount
    ) {
        public SituationalPersonalitySnapshot {
            modeIntensities    = modeIntensities != null ? Map.copyOf(modeIntensities) : Map.of();
            facetDisplacements = facetDisplacements != null ? Map.copyOf(facetDisplacements) : Map.of();
        }
    }
}

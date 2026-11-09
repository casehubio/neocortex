package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.prompt.observation.RegulationTracker;
import java.util.List;
import java.util.Map;

public final class SocialContextInputComputer {

    private static final int SATURATING_COUNT = 3;
    private static final double TRUST_WEIGHT = 0.6;
    private static final double TRAJECTORY_WEIGHT = 0.4;

    private SocialContextInputComputer() {}

    public static double computeIsolation(RegulationContext ctx) {
        if (ctx == null || ctx.interactingAgents() == null) return 1.0;
        int count = ctx.interactingAgents().size();
        return 1.0 - clamp01((double) count / SATURATING_COUNT);
    }

    public static double computeResponsiveness(double trust,
                                                RegulationTracker.Trajectory trajectory) {
        double trajectorySignal = trajectory == null ? 0.5
            : switch (trajectory) {
                case IMPROVING -> 1.0;
                case STABLE -> 0.5;
                case WORSENING -> 0.0;
            };
        return clamp01(TRUST_WEIGHT * trust + TRAJECTORY_WEIGHT * trajectorySignal);
    }

    public static double computeDistress(double pleasure, double arousal) {
        return clamp01((-pleasure + arousal) / 2.0);
    }

    public static double computePowerAsymmetry(double dominanceScore,
                                                double submissionScore,
                                                double socialComparisonBias) {
        return clamp01((dominanceScore - submissionScore + socialComparisonBias) / 2.0);
    }

    public static Map<String, Double> computeFromRawInputs(
            RegulationContext ctx,
            double maxTrust,
            RegulationTracker.Trajectory trajectory,
            double targetPleasure,
            double targetArousal,
            double dominanceScore,
            double submissionScore,
            double socialComparisonBias) {
        return Map.of(
            "perceived_responsiveness", computeResponsiveness(maxTrust, trajectory),
            "target_distress", computeDistress(targetPleasure, targetArousal),
            "social_isolation", computeIsolation(ctx),
            "power_asymmetry", computePowerAsymmetry(dominanceScore, submissionScore, socialComparisonBias)
        );
    }

    private static double clamp01(double x) {
        return Math.max(0, Math.min(1, x));
    }
}

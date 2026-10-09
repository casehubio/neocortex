package io.casehub.neocortex.cognition.drive;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for drive composition and modulation.
 * <p>
 * Controls how raw drive intensities are modulated by mood and
 * personality, and how the composite drive score is computed.
 * The modulation model follows Self-Determination Theory's distinction
 * between intrinsic drives (curiosity, competence, autonomy) and
 * social drives (affiliation).
 *
 * @see Deci, E.L. & Ryan, R.M. (2000). The "what" and "why" of goal
 * pursuits: Human needs and the self-determination of behavior.
 * Psychological Inquiry, 11(4), 227-268.
 */
public record DriveConfig(
        Map<DriveAxis, Double> axisWeights,
        double changeThreshold,
        double moodPleasureModulation,
        double moodArousalModulation,
        double personalityModulationStrength,
        double maxIntensity,
        double minIntensity,
        double affiliationDecayThreshold,
        Duration affiliationStaleDuration,
        double autonomyConfidenceFloor,
        double narrativeModulationStrength,
        double subThoughtModulationStrength,

        /**
         * Scaling factor for mood pleasure modulation on drives that are
         * not directly pleasure-linked (CURIOSITY, AUTONOMY).
         *
         * AFFILIATION and COMPETENCE receive the full moodPleasureModulation
         * because positive affect directly facilitates social engagement and
         * perceived competence. Other drives receive a dampened version.
         *
         * Range: [0.0, 1.0]. Default 0.5 — half the pleasure modulation
         * strength, preventing mood from dominating non-social drives.
         */
        double secondaryPleasureModulationFactor
) {
    public DriveConfig {
        Objects.requireNonNull(axisWeights, "axisWeights required");
        Objects.requireNonNull(affiliationStaleDuration, "affiliationStaleDuration required");
        axisWeights = Map.copyOf(axisWeights);
        if (changeThreshold < 0.0 || changeThreshold > 1.0) {
            throw new IllegalArgumentException("changeThreshold must be in [0.0, 1.0]");
        }
        if (maxIntensity < minIntensity) {throw new IllegalArgumentException("maxIntensity must be >= minIntensity");}
        if (minIntensity < 0.0) {throw new IllegalArgumentException("minIntensity must be >= 0.0");}
        if (maxIntensity > 1.0) {throw new IllegalArgumentException("maxIntensity must be <= 1.0");}
    }

    public static DriveConfig defaults() {
        return new DriveConfig(
                Map.of(DriveAxis.CURIOSITY, 1.0, DriveAxis.COMPETENCE, 1.0,
                       DriveAxis.AFFILIATION, 1.0, DriveAxis.AUTONOMY, 1.0),
                0.05, 0.3, 0.2, 0.25, 1.0, 0.0,
                0.5, Duration.ofHours(24), 0.6, 0.25, 0.6,
                0.5);
    }
}

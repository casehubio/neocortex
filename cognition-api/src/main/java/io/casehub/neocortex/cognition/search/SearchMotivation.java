package io.casehub.neocortex.cognition.search;

import io.casehub.neocortex.cognition.drive.DriveAxis;

/**
 * Dominant motivation source shaping search character.
 * <p>
 * CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY map from {@link DriveAxis}
 * when a drive is dominant. THREAT maps from
 * {@link io.casehub.neocortex.cognition.appraisal.ActionReadiness#AVOIDANCE}
 * intensity. GOAL maps from GoalPrioritizationPhase max priority.
 * <p>
 * Source selection: highest-intensity signal across all subsystems wins
 * (Frijda, 1986 control precedence).
 */
public enum SearchMotivation {
    CURIOSITY,
    COMPETENCE,
    AFFILIATION,
    AUTONOMY,
    THREAT,
    GOAL;

    public static SearchMotivation fromDriveAxis(DriveAxis axis) {
        return switch (axis) {
            case CURIOSITY -> CURIOSITY;
            case COMPETENCE -> COMPETENCE;
            case AFFILIATION -> AFFILIATION;
            case AUTONOMY -> AUTONOMY;
        };
    }
}

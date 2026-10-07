package io.casehub.neocortex.cognition.need;

import io.casehub.neocortex.cognition.drive.DriveAxis;

public enum NeedTier {
    SAFETY, TASKS, SOCIAL, SELF_EXPRESSION, UNDERSTANDING;

    public static NeedTier fromDriveAxis(DriveAxis axis) {
        return switch (axis) {
            case CURIOSITY -> UNDERSTANDING;
            case COMPETENCE -> SELF_EXPRESSION;
            case AFFILIATION -> SOCIAL;
            case AUTONOMY -> SELF_EXPRESSION;
        };
    }
}

package io.casehub.neocortex.cognition.drive;

import java.util.Objects;

public record DriveReinforcementEntry(
    String driveType,
    ReinforcementDirection direction,
    RewardAxis rewardAxis
) {
    public DriveReinforcementEntry {
        Objects.requireNonNull(driveType, "driveType required");
        if (direction == null) direction = ReinforcementDirection.POSITIVE;
        if (rewardAxis == null) rewardAxis = RewardAxis.PLEASURE;
    }
}

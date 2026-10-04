package io.casehub.neocortex.cognition.drive;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DriveProfile(
        String agentId,
        String tenantId,
        Map<DriveAxis, DriveIntensity> drives,
        double compositeMotivation,
        DriveAxis dominantDrive,
        Instant evaluatedAt) {
    public DriveProfile {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(drives, "drives required");
        Objects.requireNonNull(dominantDrive, "dominantDrive required");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt required");
        if (compositeMotivation < 0.0 || compositeMotivation > 1.0) {
            throw new IllegalArgumentException(
                    "compositeMotivation must be in [0.0, 1.0], got " + compositeMotivation);
        }
        drives = Map.copyOf(drives);
    }

    public List<io.casehub.neocortex.cognition.appraisal.Drive> allDrives() {
        return drives.entrySet().stream()
                     .map(e -> new io.casehub.neocortex.cognition.appraisal.Drive(
                             e.getKey().name().toLowerCase(),
                             io.casehub.neocortex.cognition.appraisal.DriveCategory.BASELINE,
                             e.getValue().intensity(),
                             e.getValue().trigger()))
                     .toList();
    }

    public String dominantDriveName() {
        return dominantDrive.name().toLowerCase();
    }
}

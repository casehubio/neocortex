package io.casehub.neocortex.cognition.innerlife;

import java.time.Duration;

public record NonVerbalConfig(
        Duration silenceThreshold,
        double affiliationDriveThreshold,
        String defaultTag) {

    public static final Duration DEFAULT_SILENCE_THRESHOLD = Duration.ofMinutes(5);
    public static final double DEFAULT_AFFILIATION_DRIVE_THRESHOLD = 0.6;
    public static final String DEFAULT_TAG = "knock";

    public NonVerbalConfig {
        if (silenceThreshold == null) {
            throw new IllegalArgumentException("silenceThreshold must not be null");
        }
        if (affiliationDriveThreshold < 0.0 || affiliationDriveThreshold > 1.0) {
            throw new IllegalArgumentException(
                    "affiliationDriveThreshold must be in [0.0, 1.0], got " + affiliationDriveThreshold);
        }
        if (defaultTag == null || defaultTag.isBlank()) {
            throw new IllegalArgumentException("defaultTag must not be null or blank");
        }
    }

    public static NonVerbalConfig defaults() {
        return new NonVerbalConfig(
                DEFAULT_SILENCE_THRESHOLD,
                DEFAULT_AFFILIATION_DRIVE_THRESHOLD,
                DEFAULT_TAG);
    }
}

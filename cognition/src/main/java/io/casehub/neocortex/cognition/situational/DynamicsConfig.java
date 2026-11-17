package io.casehub.neocortex.cognition.situational;

import java.time.Duration;

public record DynamicsConfig(
        double alphaPull,
        Duration tauReturn,
        double sMax,
        double dMax,
        double tauFatigue,
        Duration tauHeal,
        double rMin,
        double tauDrift,
        double wTail,
        Duration hold,
        Duration tauSetpoint,
        double epsilonPublish
) {
    public static DynamicsConfig defaults() {
        return new DynamicsConfig(
                0.30,
                Duration.ofHours(6),
                0.25,
                0.10,
                12.0,
                Duration.ofDays(30),
                0.2,
                15.0,
                0.1,
                Duration.ofMinutes(10),
                Duration.ofDays(180),
                0.01
        );
    }
}

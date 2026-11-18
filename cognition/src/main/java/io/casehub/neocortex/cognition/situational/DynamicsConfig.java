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
        double epsilonPublish,
        double promoteFraction,
        Duration promoteDwell,
        double promotionBudget
) {
    public DynamicsConfig {
        if (promoteFraction <= 0 || promoteFraction > 1)
            throw new IllegalArgumentException("promoteFraction must be in (0, 1]");
        if (promoteDwell == null || promoteDwell.isNegative() || promoteDwell.isZero())
            throw new IllegalArgumentException("promoteDwell must be positive");
        if (promotionBudget <= 0 || promotionBudget > 1)
            throw new IllegalArgumentException("promotionBudget must be in (0, 1]");
        if (dMax <= 0 || dMax > 0.5)
            throw new IllegalArgumentException("dMax must be in (0, 0.5]");
        if (sMax <= 0)
            throw new IllegalArgumentException("sMax must be positive");
        if (epsilonPublish >= promoteFraction * dMax)
            throw new IllegalArgumentException("epsilonPublish must be < promoteFraction * dMax");
        if (promotionBudget < promoteFraction * dMax)
            throw new IllegalArgumentException("promotionBudget must be >= promoteFraction * dMax");
    }

    public DynamicsConfig(double alphaPull, Duration tauReturn, double sMax, double dMax,
                          double tauFatigue, Duration tauHeal, double rMin, double tauDrift,
                          double wTail, Duration hold, Duration tauSetpoint, double epsilonPublish) {
        this(alphaPull, tauReturn, sMax, dMax, tauFatigue, tauHeal, rMin, tauDrift,
             wTail, hold, tauSetpoint, epsilonPublish, 0.8, Duration.ofDays(30), 0.20);
    }

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
                0.01,
                0.8,
                Duration.ofDays(30),
                0.20
        );
    }
}

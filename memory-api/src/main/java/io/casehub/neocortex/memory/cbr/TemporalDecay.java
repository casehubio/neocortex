package io.casehub.neocortex.memory.cbr;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public sealed interface TemporalDecay {

    double factor(Instant storedAt, Instant now);

    record HalfLife(Duration halfLife) implements TemporalDecay {
        public HalfLife {
            Objects.requireNonNull(halfLife, "halfLife required");
            if (halfLife.isNegative() || halfLife.isZero()) {
                throw new IllegalArgumentException("halfLife must be positive, got " + halfLife);
            }
        }

        @Override
        public double factor(Instant storedAt, Instant now) {
            if (storedAt == null) {return 1.0;}
            double ageSeconds = Duration.between(storedAt, now).toSeconds();
            if (ageSeconds <= 0) {return 1.0;}
            double halfLifeSeconds = halfLife.toSeconds();
            return Math.pow(0.5, ageSeconds / halfLifeSeconds);
        }
    }

    record Linear(Duration zeroAt) implements TemporalDecay {
        public Linear {
            Objects.requireNonNull(zeroAt, "zeroAt required");
            if (zeroAt.isNegative() || zeroAt.isZero()) {
                throw new IllegalArgumentException("zeroAt must be positive, got " + zeroAt);
            }
        }

        @Override
        public double factor(Instant storedAt, Instant now) {
            if (storedAt == null) {return 1.0;}
            double ageSeconds = Duration.between(storedAt, now).toSeconds();
            if (ageSeconds <= 0) {return 1.0;}
            double zeroAtSeconds = zeroAt.toSeconds();
            return Math.max(0.0, 1.0 - ageSeconds / zeroAtSeconds);
        }
    }

    record Step(Duration cutoff, double afterCutoff) implements TemporalDecay {
        public Step {
            Objects.requireNonNull(cutoff, "cutoff required");
            if (cutoff.isNegative() || cutoff.isZero()) {
                throw new IllegalArgumentException("cutoff must be positive, got " + cutoff);
            }
            if (afterCutoff < 0.0 || afterCutoff > 1.0) {
                throw new IllegalArgumentException("afterCutoff must be in [0,1], got " + afterCutoff);
            }
        }

        @Override
        public double factor(Instant storedAt, Instant now) {
            if (storedAt == null) {return 1.0;}
            double ageSeconds = Duration.between(storedAt, now).toSeconds();
            if (ageSeconds <= 0) {return 1.0;}
            return ageSeconds < cutoff.toSeconds() ? 1.0 : afterCutoff;
        }
    }
}

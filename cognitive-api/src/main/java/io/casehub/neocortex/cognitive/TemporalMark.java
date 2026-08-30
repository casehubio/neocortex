package io.casehub.neocortex.cognitive;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public sealed interface TemporalMark {

    Instant resolveToInstant(Instant now);

    record WallClock(Instant instant) implements TemporalMark {
        public WallClock {
            Objects.requireNonNull(instant, "instant required");
        }

        @Override
        public Instant resolveToInstant(Instant now) {
            return instant;
        }
    }

    record Relative(Duration offset, Instant anchor) implements TemporalMark {
        public Relative {
            Objects.requireNonNull(offset, "offset required");
        }

        @Override
        public Instant resolveToInstant(Instant now) {
            Instant base = anchor != null ? anchor : now;
            return base.plus(offset);
        }
    }

    record Ordinal(String turnId, Instant resolved) implements TemporalMark {
        public Ordinal {
            Objects.requireNonNull(turnId, "turnId required");
            Objects.requireNonNull(resolved, "resolved required");
        }

        @Override
        public Instant resolveToInstant(Instant now) {
            return resolved;
        }
    }

    static TemporalMark wallClock(Instant instant) {
        return new WallClock(instant);
    }

    static TemporalMark relativeToNow(Duration offset) {
        return new Relative(offset, null);
    }

    static TemporalMark relativeToAnchor(Duration offset, Instant anchor) {
        return new Relative(offset, Objects.requireNonNull(anchor, "anchor required"));
    }

    static TemporalMark ordinal(String turnId, Instant resolved) {
        return new Ordinal(turnId, resolved);
    }
}

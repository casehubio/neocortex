package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TemporalMarkTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private static final Instant MEETING = Instant.parse("2026-12-25T15:00:00Z");

    // --- WallClock ---

    @Test
    void wallClock_resolvesToWrappedInstant() {
        var mark = TemporalMark.wallClock(MEETING);
        assertThat(mark.resolveToInstant(NOW)).isEqualTo(MEETING);
    }

    @Test
    void wallClock_ignoresNow() {
        var mark = TemporalMark.wallClock(MEETING);
        var differentNow = Instant.parse("2099-01-01T00:00:00Z");
        assertThat(mark.resolveToInstant(differentNow)).isEqualTo(MEETING);
    }

    @Test
    void wallClock_nullInstantThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> TemporalMark.wallClock(null));
    }

    @Test
    void wallClock_recordAccessor() {
        var mark = (TemporalMark.WallClock) TemporalMark.wallClock(MEETING);
        assertThat(mark.instant()).isEqualTo(MEETING);
    }

    // --- Relative ---

    @Test
    void relative_nullAnchor_resolvesFromNow() {
        var mark = TemporalMark.relativeToNow(Duration.ofDays(3));
        assertThat(mark.resolveToInstant(NOW)).isEqualTo(NOW.plus(Duration.ofDays(3)));
    }

    @Test
    void relative_withAnchor_resolvesFromAnchor() {
        var mark = TemporalMark.relativeToAnchor(Duration.ofDays(3), MEETING);
        assertThat(mark.resolveToInstant(NOW)).isEqualTo(MEETING.plus(Duration.ofDays(3)));
    }

    @Test
    void relative_negativeOffset_pastReference() {
        var mark = TemporalMark.relativeToNow(Duration.ofDays(-7));
        assertThat(mark.resolveToInstant(NOW)).isEqualTo(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void relative_nullAnchor_differentNowProducesDifferentResult() {
        var mark = TemporalMark.relativeToNow(Duration.ofHours(1));
        var now1 = Instant.parse("2026-08-30T10:00:00Z");
        var now2 = Instant.parse("2026-08-30T14:00:00Z");
        assertThat(mark.resolveToInstant(now1)).isNotEqualTo(mark.resolveToInstant(now2));
    }

    @Test
    void relative_nullOffsetThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> TemporalMark.relativeToNow(null));
    }

    @Test
    void relative_relativeToAnchor_nullAnchorThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> TemporalMark.relativeToAnchor(Duration.ofDays(1), null));
    }

    @Test
    void relative_recordAccessors() {
        var mark = (TemporalMark.Relative) TemporalMark.relativeToAnchor(Duration.ofDays(3), MEETING);
        assertThat(mark.offset()).isEqualTo(Duration.ofDays(3));
        assertThat(mark.anchor()).isEqualTo(MEETING);
    }

    @Test
    void relative_nullAnchorAccessor() {
        var mark = (TemporalMark.Relative) TemporalMark.relativeToNow(Duration.ofDays(1));
        assertThat(mark.anchor()).isNull();
    }

    // --- Ordinal ---

    @Test
    void ordinal_resolvesToPreResolvedInstant() {
        var mark = TemporalMark.ordinal("turn-42", MEETING);
        assertThat(mark.resolveToInstant(NOW)).isEqualTo(MEETING);
    }

    @Test
    void ordinal_ignoresNow() {
        var mark = TemporalMark.ordinal("turn-42", MEETING);
        var differentNow = Instant.parse("2099-01-01T00:00:00Z");
        assertThat(mark.resolveToInstant(differentNow)).isEqualTo(MEETING);
    }

    @Test
    void ordinal_nullTurnIdThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> TemporalMark.ordinal(null, MEETING));
    }

    @Test
    void ordinal_nullResolvedThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> TemporalMark.ordinal("turn-1", null));
    }

    @Test
    void ordinal_recordAccessors() {
        var mark = (TemporalMark.Ordinal) TemporalMark.ordinal("turn-42", MEETING);
        assertThat(mark.turnId()).isEqualTo("turn-42");
        assertThat(mark.resolved()).isEqualTo(MEETING);
    }

    // --- Pattern matching and equality ---

    @Test
    void patternMatching_exhaustive() {
        TemporalMark mark = TemporalMark.wallClock(NOW);
        String result = switch (mark) {
            case TemporalMark.WallClock wc -> "wall:" + wc.instant();
            case TemporalMark.Relative rel -> "rel:" + rel.offset();
            case TemporalMark.Ordinal ord -> "ord:" + ord.turnId();
        };
        assertThat(result).startsWith("wall:");
    }

    @Test
    void equality_sameWallClockEqual() {
        assertThat(TemporalMark.wallClock(MEETING))
                .isEqualTo(TemporalMark.wallClock(MEETING));
    }

    @Test
    void equality_sameRelativeEqual() {
        assertThat(TemporalMark.relativeToAnchor(Duration.ofDays(1), MEETING))
                .isEqualTo(TemporalMark.relativeToAnchor(Duration.ofDays(1), MEETING));
    }

    @Test
    void equality_sameOrdinalEqual() {
        assertThat(TemporalMark.ordinal("t1", MEETING))
                .isEqualTo(TemporalMark.ordinal("t1", MEETING));
    }

    @Test
    void equality_differentVariantsNotEqual() {
        assertThat(TemporalMark.wallClock(MEETING))
                .isNotEqualTo(TemporalMark.ordinal("t1", MEETING));
    }
}

package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceRuleTest {

    @Test
    void parse_weeklyMonday() {
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=MO");
        assertThat(rule.freq()).isEqualTo(RecurrenceRule.Frequency.WEEKLY);
        assertThat(rule.interval()).isEqualTo(1);
        assertThat(rule.count()).isNull();
        assertThat(rule.until()).isNull();
        assertThat(rule.byDay()).containsExactly(DayOfWeek.MONDAY);
    }

    @Test
    void parse_dailyWithInterval() {
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=DAILY;INTERVAL=3");
        assertThat(rule.freq()).isEqualTo(RecurrenceRule.Frequency.DAILY);
        assertThat(rule.interval()).isEqualTo(3);
    }

    @Test
    void parse_monthlyWithCount() {
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=MONTHLY;COUNT=12");
        assertThat(rule.freq()).isEqualTo(RecurrenceRule.Frequency.MONTHLY);
        assertThat(rule.count()).isEqualTo(12);
    }

    @Test
    void parse_yearlyWithUntil() {
        Instant until = Instant.parse("2027-12-31T23:59:59Z");
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=YEARLY;UNTIL=20271231T235959Z");
        assertThat(rule.freq()).isEqualTo(RecurrenceRule.Frequency.YEARLY);
        assertThat(rule.until()).isEqualTo(until);
    }

    @Test
    void parse_multipleDays() {
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=MO,WE,FR");
        assertThat(rule.byDay()).containsExactlyInAnyOrder(
            DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void parse_missingFreq_throws() {
        assertThatThrownBy(() -> RecurrenceRule.parse("INTERVAL=2"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString_roundTrip() {
        String original = "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR";
        RecurrenceRule rule = RecurrenceRule.parse(original);
        RecurrenceRule reparsed = RecurrenceRule.parse(rule.toString());
        assertThat(reparsed).isEqualTo(rule);
    }

    @Test
    void toString_minimalDaily() {
        RecurrenceRule rule = new RecurrenceRule(
            RecurrenceRule.Frequency.DAILY, 1, null, null, Set.of());
        assertThat(rule.toString()).isEqualTo("FREQ=DAILY");
    }

    @Test
    void defaultInterval_isOne() {
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=DAILY");
        assertThat(rule.interval()).isEqualTo(1);
    }
}

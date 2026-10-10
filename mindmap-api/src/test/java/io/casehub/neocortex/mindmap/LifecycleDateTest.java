package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static io.casehub.neocortex.mindmap.LifecycleDate.Qualifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleDateTest {

    // --- Precision parsing ---

    @Test
    void parse_fullDate() {
        LifecycleDate date = LifecycleDate.parse("1907-07-06");
        assertThat(date.precision()).isEqualTo(LifecycleDate.Precision.DAY);
        assertThat(date.qualifier()).isEqualTo(Qualifier.NONE);
        assertThat(date.toString()).isEqualTo("1907-07-06");
    }

    @Test
    void parse_yearMonth() {
        LifecycleDate date = LifecycleDate.parse("1938-11");
        assertThat(date.precision()).isEqualTo(LifecycleDate.Precision.MONTH);
        assertThat(date.qualifier()).isEqualTo(Qualifier.NONE);
        assertThat(date.toString()).isEqualTo("1938-11");
    }

    @Test
    void parse_yearOnly() {
        LifecycleDate date = LifecycleDate.parse("1939");
        assertThat(date.precision()).isEqualTo(LifecycleDate.Precision.YEAR);
        assertThat(date.qualifier()).isEqualTo(Qualifier.NONE);
        assertThat(date.toString()).isEqualTo("1939");
    }

    // --- EDTF qualifiers (postfix) ---

    @Test
    void parse_approximate() {
        LifecycleDate date = LifecycleDate.parse("1904~");
        assertThat(date.precision()).isEqualTo(LifecycleDate.Precision.YEAR);
        assertThat(date.qualifier()).isEqualTo(Qualifier.APPROXIMATE);
        assertThat(date.isApproximate()).isTrue();
        assertThat(date.isUncertain()).isFalse();
        assertThat(date.toString()).isEqualTo("1904~");
    }

    @Test
    void parse_uncertain() {
        LifecycleDate date = LifecycleDate.parse("1904?");
        assertThat(date.precision()).isEqualTo(LifecycleDate.Precision.YEAR);
        assertThat(date.qualifier()).isEqualTo(Qualifier.UNCERTAIN);
        assertThat(date.isApproximate()).isFalse();
        assertThat(date.isUncertain()).isTrue();
        assertThat(date.toString()).isEqualTo("1904?");
    }

    @Test
    void parse_uncertainApproximate() {
        LifecycleDate date = LifecycleDate.parse("1904%");
        assertThat(date.precision()).isEqualTo(LifecycleDate.Precision.YEAR);
        assertThat(date.qualifier()).isEqualTo(Qualifier.UNCERTAIN_APPROXIMATE);
        assertThat(date.isApproximate()).isTrue();
        assertThat(date.isUncertain()).isTrue();
        assertThat(date.toString()).isEqualTo("1904%");
    }

    @Test
    void parse_approximateWithMonth() {
        LifecycleDate date = LifecycleDate.parse("1938-11~");
        assertThat(date.precision()).isEqualTo(LifecycleDate.Precision.MONTH);
        assertThat(date.isApproximate()).isTrue();
        assertThat(date.toString()).isEqualTo("1938-11~");
    }

    @Test
    void parse_approximateWithFullDate() {
        LifecycleDate date = LifecycleDate.parse("1907-07-06~");
        assertThat(date.precision()).isEqualTo(LifecycleDate.Precision.DAY);
        assertThat(date.isApproximate()).isTrue();
        assertThat(date.toString()).isEqualTo("1907-07-06~");
    }

    @Test
    void parse_uncertainWithMonth() {
        LifecycleDate date = LifecycleDate.parse("1938-11?");
        assertThat(date.precision()).isEqualTo(LifecycleDate.Precision.MONTH);
        assertThat(date.isUncertain()).isTrue();
        assertThat(date.toString()).isEqualTo("1938-11?");
    }

    // --- Instant derivation ---

    @Test
    void toStartInstant_fullDate() {
        LifecycleDate date = LifecycleDate.parse("1907-07-06");
        Instant expected = LocalDate.of(1907, 7, 6).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(date.toStartInstant()).isEqualTo(expected);
    }

    @Test
    void toStartInstant_yearMonth() {
        LifecycleDate date = LifecycleDate.parse("1938-11");
        Instant expected = YearMonth.of(1938, 11).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(date.toStartInstant()).isEqualTo(expected);
    }

    @Test
    void toStartInstant_yearOnly() {
        LifecycleDate date = LifecycleDate.parse("1939");
        Instant expected = Year.of(1939).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(date.toStartInstant()).isEqualTo(expected);
    }

    @Test
    void toEndInstant_fullDate() {
        LifecycleDate date = LifecycleDate.parse("1954-07-13");
        Instant expected = LocalDate.of(1954, 7, 13).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(date.toEndInstant()).isEqualTo(expected);
    }

    @Test
    void toEndInstant_yearMonth() {
        LifecycleDate date = LifecycleDate.parse("1938-11");
        Instant expected = YearMonth.of(1938, 11).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(date.toEndInstant()).isEqualTo(expected);
    }

    @Test
    void toEndInstant_yearOnly() {
        LifecycleDate date = LifecycleDate.parse("1939");
        Instant expected = Year.of(1940).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(date.toEndInstant()).isEqualTo(expected);
    }

    @Test
    void qualifier_preservedInInstants() {
        LifecycleDate exact = LifecycleDate.parse("1939");
        LifecycleDate approx = LifecycleDate.parse("1939~");
        LifecycleDate uncertain = LifecycleDate.parse("1939?");
        assertThat(approx.toStartInstant()).isEqualTo(exact.toStartInstant());
        assertThat(approx.toEndInstant()).isEqualTo(exact.toEndInstant());
        assertThat(uncertain.toStartInstant()).isEqualTo(exact.toStartInstant());
    }

    // --- Invalid inputs ---

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "12", "12345", "1907-13", "1907-07-32", "~", "?", "%"})
    void parse_invalidInputs(String input) {
        assertThatThrownBy(() -> LifecycleDate.parse(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_null() {
        assertThatThrownBy(() -> LifecycleDate.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Equality ---

    @Test
    void equality() {
        assertThat(LifecycleDate.parse("1907-07-06")).isEqualTo(LifecycleDate.parse("1907-07-06"));
        assertThat(LifecycleDate.parse("1904~")).isEqualTo(LifecycleDate.parse("1904~"));
        assertThat(LifecycleDate.parse("1907")).isNotEqualTo(LifecycleDate.parse("1907~"));
        assertThat(LifecycleDate.parse("1907~")).isNotEqualTo(LifecycleDate.parse("1907?"));
        assertThat(LifecycleDate.parse("1907?")).isNotEqualTo(LifecycleDate.parse("1907%"));
    }

    // --- Ordering ---

    @Test
    void compareTo_ordersChronologically() {
        LifecycleDate d1 = LifecycleDate.parse("1907");
        LifecycleDate d2 = LifecycleDate.parse("1938-11");
        LifecycleDate d3 = LifecycleDate.parse("1954-07-13");
        assertThat(d1).isLessThan(d2);
        assertThat(d2).isLessThan(d3);
    }

    @Test
    void compareTo_sameDateDifferentPrecision() {
        LifecycleDate year = LifecycleDate.parse("1907");
        LifecycleDate month = LifecycleDate.parse("1907-07");
        LifecycleDate day = LifecycleDate.parse("1907-07-06");
        assertThat(year).isLessThan(month);
        assertThat(month).isLessThan(day);
    }

    // --- Accessors ---

    @Test
    void year_accessor() {
        assertThat(LifecycleDate.parse("1907-07-06").year()).isEqualTo(1907);
        assertThat(LifecycleDate.parse("1938-11").year()).isEqualTo(1938);
        assertThat(LifecycleDate.parse("1939").year()).isEqualTo(1939);
    }

    @Test
    void month_accessor() {
        assertThat(LifecycleDate.parse("1907-07-06").month()).hasValue(7);
        assertThat(LifecycleDate.parse("1938-11").month()).hasValue(11);
        assertThat(LifecycleDate.parse("1939").month()).isEmpty();
    }

    @Test
    void day_accessor() {
        assertThat(LifecycleDate.parse("1907-07-06").day()).hasValue(6);
        assertThat(LifecycleDate.parse("1938-11").day()).isEmpty();
        assertThat(LifecycleDate.parse("1939").day()).isEmpty();
    }
}

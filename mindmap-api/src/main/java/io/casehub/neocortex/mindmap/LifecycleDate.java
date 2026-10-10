package io.casehub.neocortex.mindmap;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * EDTF Level 1 date — partial ISO 8601 date with optional qualification.
 * Implements the date subset of ISO 8601-2:2019 (Extended Date/Time Format).
 *
 * <p>Supports YYYY, YYYY-MM, and YYYY-MM-DD with optional postfix qualifier:
 * {@code ~} (approximate), {@code ?} (uncertain), {@code %} (both).
 *
 * @see <a href="https://www.loc.gov/standards/datetime/">Library of Congress EDTF</a>
 */
public final class LifecycleDate implements Comparable<LifecycleDate> {

    public enum Precision { YEAR, MONTH, DAY }

    public enum Qualifier {
        NONE,
        APPROXIMATE,
        UNCERTAIN,
        UNCERTAIN_APPROXIMATE;

        char suffix() {
            return switch (this) {
                case NONE -> 0;
                case APPROXIMATE -> '~';
                case UNCERTAIN -> '?';
                case UNCERTAIN_APPROXIMATE -> '%';
            };
        }

        static Qualifier fromSuffix(char c) {
            return switch (c) {
                case '~' -> APPROXIMATE;
                case '?' -> UNCERTAIN;
                case '%' -> UNCERTAIN_APPROXIMATE;
                default -> NONE;
            };
        }
    }

    private final int year;
    private final int month; // 0 = not set
    private final int day;   // 0 = not set
    private final Qualifier qualifier;
    private final Precision precision;

    private LifecycleDate(int year, int month, int day, Qualifier qualifier) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.qualifier = qualifier;
        this.precision = day > 0 ? Precision.DAY : month > 0 ? Precision.MONTH : Precision.YEAR;
    }

    public static LifecycleDate parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invalid EDTF date: " + value);
        }
        String s = value.strip();

        Qualifier qual = Qualifier.NONE;
        char last = s.charAt(s.length() - 1);
        if (last == '~' || last == '?' || last == '%') {
            qual = Qualifier.fromSuffix(last);
            s = s.substring(0, s.length() - 1);
            if (s.isEmpty()) {
                throw new IllegalArgumentException("Invalid EDTF date: " + value);
            }
        }

        String[] parts = s.split("-");
        if (parts.length < 1 || parts.length > 3) {
            throw new IllegalArgumentException("Invalid EDTF date: " + value);
        }

        if (parts[0].length() != 4) {
            throw new IllegalArgumentException("Invalid EDTF date: " + value);
        }
        int y = parseComponent(parts[0], value, 1, 9999);

        int m = 0;
        int d = 0;
        if (parts.length >= 2) {
            m = parseComponent(parts[1], value, 1, 12);
        }
        if (parts.length == 3) {
            d = parseComponent(parts[2], value, 1, 31);
            LocalDate.of(y, m, d);
        }

        return new LifecycleDate(y, m, d, qual);
    }

    private static int parseComponent(String s, String original, int min, int max) {
        try {
            int val = Integer.parseInt(s);
            if (val < min || val > max) {
                throw new IllegalArgumentException("Invalid EDTF date: " + original);
            }
            return val;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid EDTF date: " + original);
        }
    }

    public Precision precision() { return precision; }

    public Qualifier qualifier() { return qualifier; }

    public boolean isApproximate() {
        return qualifier == Qualifier.APPROXIMATE || qualifier == Qualifier.UNCERTAIN_APPROXIMATE;
    }

    public boolean isUncertain() {
        return qualifier == Qualifier.UNCERTAIN || qualifier == Qualifier.UNCERTAIN_APPROXIMATE;
    }

    public int year() { return year; }

    public OptionalInt month() { return month > 0 ? OptionalInt.of(month) : OptionalInt.empty(); }

    public OptionalInt day() { return day > 0 ? OptionalInt.of(day) : OptionalInt.empty(); }

    public Instant toStartInstant() {
        return switch (precision) {
            case DAY -> LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant();
            case MONTH -> YearMonth.of(year, month).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            case YEAR -> Year.of(year).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        };
    }

    public Instant toEndInstant() {
        return switch (precision) {
            case DAY -> LocalDate.of(year, month, day).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            case MONTH -> YearMonth.of(year, month).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            case YEAR -> Year.of(year + 1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        };
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(String.format("%04d", year));
        if (month > 0) {
            sb.append(String.format("-%02d", month));
            if (day > 0) {
                sb.append(String.format("-%02d", day));
            }
        }
        if (qualifier != Qualifier.NONE) {
            sb.append(qualifier.suffix());
        }
        return sb.toString();
    }

    @Override
    public int compareTo(LifecycleDate other) {
        int cmp = Integer.compare(this.year, other.year);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.month, other.month);
        if (cmp != 0) return cmp;
        return Integer.compare(this.day, other.day);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LifecycleDate that)) return false;
        return year == that.year && month == that.month && day == that.day && qualifier == that.qualifier;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, month, day, qualifier);
    }
}

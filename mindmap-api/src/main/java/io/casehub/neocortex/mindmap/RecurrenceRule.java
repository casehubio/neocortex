package io.casehub.neocortex.mindmap;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public record RecurrenceRule(
    Frequency freq,
    int interval,
    Integer count,
    Instant until,
    Set<DayOfWeek> byDay
) {
    public enum Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    private static final DateTimeFormatter UNTIL_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private static final Map<String, DayOfWeek> DAY_MAP = Map.of(
        "MO", DayOfWeek.MONDAY, "TU", DayOfWeek.TUESDAY,
        "WE", DayOfWeek.WEDNESDAY, "TH", DayOfWeek.THURSDAY,
        "FR", DayOfWeek.FRIDAY, "SA", DayOfWeek.SATURDAY,
        "SU", DayOfWeek.SUNDAY);

    private static final Map<DayOfWeek, String> DAY_REVERSE;
    static {
        DAY_REVERSE = new EnumMap<>(DayOfWeek.class);
        DAY_MAP.forEach((k, v) -> DAY_REVERSE.put(v, k));
    }

    public RecurrenceRule {
        if (freq == null) throw new IllegalArgumentException("freq must not be null");
        if (interval < 1) throw new IllegalArgumentException("interval must be >= 1");
        byDay = byDay == null ? Set.of() : Set.copyOf(byDay);
    }

    public static RecurrenceRule parse(String rrule) {
        Frequency freq = null;
        int interval = 1;
        Integer count = null;
        Instant until = null;
        Set<DayOfWeek> byDay = new LinkedHashSet<>();

        for (String part : rrule.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            switch (kv[0]) {
                case "FREQ" -> freq = Frequency.valueOf(kv[1]);
                case "INTERVAL" -> interval = Integer.parseInt(kv[1]);
                case "COUNT" -> count = Integer.parseInt(kv[1]);
                case "UNTIL" -> until = UNTIL_FORMAT.parse(kv[1], Instant::from);
                case "BYDAY" -> {
                    for (String d : kv[1].split(",")) {
                        DayOfWeek day = DAY_MAP.get(d);
                        if (day == null) throw new IllegalArgumentException("Unknown day: " + d);
                        byDay.add(day);
                    }
                }
            }
        }
        if (freq == null) throw new IllegalArgumentException("FREQ is required");
        return new RecurrenceRule(freq, interval, count, until, byDay);
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(";");
        sj.add("FREQ=" + freq.name());
        if (interval > 1) sj.add("INTERVAL=" + interval);
        if (count != null) sj.add("COUNT=" + count);
        if (until != null) sj.add("UNTIL=" + UNTIL_FORMAT.format(until));
        if (!byDay.isEmpty()) {
            StringJoiner days = new StringJoiner(",");
            byDay.stream().sorted().forEach(d -> days.add(DAY_REVERSE.get(d)));
            sj.add("BYDAY=" + days);
        }
        return sj.toString();
    }
}

package io.casehub.neocortex.memory.seeding;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class FormativeTimestampGenerator {

    private FormativeTimestampGenerator() {}

    public static List<Instant> generate(int count, String period, Instant origin) {
        if (origin == null) origin = Instant.EPOCH;
        if (count <= 0) return List.of();

        long startOffset = periodStartOffset(period);
        long endOffset = periodEndOffset(period);
        long range = endOffset - startOffset;
        long step = count > 1 ? range / (count - 1) : 0;

        var timestamps = new ArrayList<Instant>(count);
        for (int i = 0; i < count; i++) {
            long offsetDays = startOffset + (step * i);
            timestamps.add(origin.plus(Duration.ofDays(offsetDays)));
        }
        return List.copyOf(timestamps);
    }

    private static long periodStartOffset(String period) {
        if (period == null) return 0;
        return switch (period) {
            case "infancy"     -> 0;
            case "childhood"   -> 365;
            case "adolescence" -> 365 * 5;
            case "adult"       -> 365 * 10;
            default            -> 0;
        };
    }

    private static long periodEndOffset(String period) {
        if (period == null) return 365 * 15;
        return switch (period) {
            case "infancy"     -> 365;
            case "childhood"   -> 365 * 5;
            case "adolescence" -> 365 * 10;
            case "adult"       -> 365 * 15;
            default            -> 365 * 15;
        };
    }
}

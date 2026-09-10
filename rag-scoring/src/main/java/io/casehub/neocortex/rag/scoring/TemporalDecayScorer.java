package io.casehub.neocortex.rag.scoring;

import io.casehub.neocortex.rag.PostRetrievalScorer;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.ScoringContext;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public class TemporalDecayScorer implements PostRetrievalScorer {

    private static final double LN2 = 0.693147;

    private final String dateKey;
    private final String tierKey;
    private final Map<Integer, Duration> tierHalfLives;
    private final Duration defaultHalfLife;

    public TemporalDecayScorer(String dateKey, String tierKey,
                                Map<Integer, Duration> tierHalfLives,
                                Duration defaultHalfLife) {
        this.dateKey = dateKey;
        this.tierKey = tierKey;
        this.tierHalfLives = Map.copyOf(tierHalfLives);
        this.defaultHalfLife = defaultHalfLife;
    }

    @Override
    public double adjust(RetrievedChunk chunk, RetrievalQuery query, ScoringContext context) {
        String dateValue = chunk.metadata().get(dateKey);
        if (dateValue == null || dateValue.isBlank()) return 1.0;

        int tier = parseTier(chunk.metadata().get(tierKey));
        Duration halfLife = tierHalfLives.getOrDefault(tier, defaultHalfLife);
        if (halfLife == null || halfLife.isZero() || halfLife.isNegative()) return 1.0;

        long ageDays;
        try {
            LocalDate submitted = LocalDate.parse(
                dateValue.length() > 10 ? dateValue.substring(0, 10) : dateValue);
            ageDays = ChronoUnit.DAYS.between(submitted, LocalDate.now());
            if (ageDays <= 0) return 1.0;
        } catch (DateTimeParseException e) {
            return 1.0;
        }

        return Math.exp(-LN2 * ageDays / halfLife.toDays());
    }

    private static int parseTier(String tier) {
        if (tier == null) return -1;
        try {
            return Integer.parseInt(tier);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

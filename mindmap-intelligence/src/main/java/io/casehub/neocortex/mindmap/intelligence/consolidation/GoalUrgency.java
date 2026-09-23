package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.MindMapNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

final class GoalUrgency {

    private static final Duration IMMEDIATE = Duration.ofHours(4);
    private static final Duration SHORT = Duration.ofDays(1);
    private static final Duration MEDIUM = Duration.ofDays(7);
    private static final Duration LONG = Duration.ofDays(30);
    private static final Duration ASPIRATIONAL = Duration.ofDays(365);

    private GoalUrgency() {
    }

    static double computeUrgency(MindMapNode node, Instant now) {
        String targetDateStr = node.property("target-date").orElse(null);
        if (targetDateStr == null) {
            return node.property("urgency").map(Double::parseDouble).orElse(0.0);
        }

        Instant deadline;
        try {
            deadline = parseTargetDate(targetDateStr);
        } catch (DateTimeParseException e) {
            return node.property("urgency").map(Double::parseDouble).orElse(0.0);
        }
        long remainingMs = Duration.between(now, deadline).toMillis();
        if (remainingMs <= 0) {return 1.0;}

        Duration budget  = horizonBudget(node.property("horizon").orElse(null));
        double   urgency = 1.0 - ((double) remainingMs / budget.toMillis());
        return Math.max(0.0, Math.min(1.0, urgency));
    }

    private static Instant parseTargetDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return Instant.parse(dateStr);
        }
    }

    private static Duration horizonBudget(String horizon) {
        if (horizon == null) return MEDIUM;
        return switch (horizon) {
            case "immediate" -> IMMEDIATE;
            case "short" -> SHORT;
            case "medium" -> MEDIUM;
            case "long" -> LONG;
            case "aspirational" -> ASPIRATIONAL;
            default -> MEDIUM;
        };
    }
}

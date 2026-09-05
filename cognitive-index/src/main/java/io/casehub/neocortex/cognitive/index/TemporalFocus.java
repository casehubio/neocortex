package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.mindmap.MindMapNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TemporalFocus {

    private TemporalFocus() {}

    public static List<AttentionItem> focus(List<TemporalEntry> entries, Instant now,
                                            Map<String, AffectTrajectory> trajectories,
                                            TemporalFocusConfig config) {
        List<AttentionItem> items = new ArrayList<>(entries.size());
        for (TemporalEntry entry : entries) {
            String entityId = extractEntityId(entry.source());
            double baseScore = baseScore(entry, now, config);
            String reason = baseReason(entry, now);

            if (entityId != null && trajectories.containsKey(entityId)) {
                AffectTrajectory trajectory = trajectories.get(entityId);
                double modifier = trajectoryModifier(trajectory, config);
                baseScore *= modifier;
                reason = appendTrajectoryReason(reason, trajectory);
            }

            items.add(new AttentionItem(entry, baseScore, reason));
        }
        items.sort(null);
        return List.copyOf(items);
    }

    public static TemporalRanker ranker(Map<String, AffectTrajectory> trajectories,
                                         TemporalFocusConfig config) {
        return (entry, now) -> {
            double score = baseScore(entry, now, config);
            String entityId = extractEntityId(entry.source());
            if (entityId != null && trajectories.containsKey(entityId)) {
                score *= trajectoryModifier(trajectories.get(entityId), config);
            }
            return score;
        };
    }

    private static double baseScore(TemporalEntry entry, Instant now, TemporalFocusConfig config) {
        return switch (entry.source()) {
            case TemporalSource.FromMindMap(MindMapNode node) -> {
                if (node.validFrom() != null && node.validFrom().isAfter(now)) {
                    double daysUntil = Duration.between(now, node.validFrom()).toHours() / 24.0;
                    yield 1.0 / (1.0 + daysUntil / config.proximityScale());
                }
                yield recencyScore(entry.timestamp(), now);
            }
            case TemporalSource.FromMemory ignored -> recencyScore(entry.timestamp(), now);
            case TemporalSource.FromCbr ignored -> recencyScore(entry.timestamp(), now);
        };
    }

    private static String baseReason(TemporalEntry entry, Instant now) {
        return switch (entry.source()) {
            case TemporalSource.FromMindMap(MindMapNode node) -> {
                if (node.validFrom() != null && node.validFrom().isAfter(now)) {
                    yield "approaching event";
                }
                yield "recent update";
            }
            case TemporalSource.FromMemory ignored -> "recent experience";
            case TemporalSource.FromCbr ignored -> "recent case";
        };
    }

    private static double trajectoryModifier(AffectTrajectory trajectory, TemporalFocusConfig config) {
        double modifier = switch (trajectory.trend()) {
            case WORSENING -> 1.0 + Math.min(config.worseningBoostCap(), Math.abs(trajectory.pleasureSlope()));
            case IMPROVING -> config.improvingDampenFactor();
            case STABLE -> 1.0;
        };

        if (trajectory.arousalVolatility() > 0.3) {
            modifier *= 1.0 + Math.min(config.volatilityBoostCap(), trajectory.arousalVolatility());
        }

        return modifier;
    }

    private static String appendTrajectoryReason(String reason, AffectTrajectory trajectory) {
        if (trajectory.trend() == TrendDirection.WORSENING) {
            return reason + " (worsening affect)";
        }
        if (trajectory.arousalVolatility() > 0.3) {
            return reason + " (volatile)";
        }
        return reason;
    }

    private static String extractEntityId(TemporalSource source) {
        return switch (source) {
            case TemporalSource.FromMindMap(MindMapNode node) -> node.id();
            case TemporalSource.FromMemory(Memory memory) -> memory.subject().id();
            case TemporalSource.FromCbr(ScoredCbrCase<?> cbrCase) -> cbrCase.caseId();
        };
    }

    private static double recencyScore(Instant timestamp, Instant now) {
        double hoursSince = Duration.between(timestamp, now).abs().toMinutes() / 60.0;
        return 1.0 / (1.0 + hoursSince);
    }
}

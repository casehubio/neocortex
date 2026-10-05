package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognitive.HabituationConfig;

import java.util.List;

public class RelevanceCheck implements SecCheck {

    @Override
    public SecResult evaluate(AppraisalContext context) {
        var narrative = context.situation().narrative();
        var drives = context.drives();

        double novelty = computeNovelty(narrative, context.habituation(), context.habituationConfig());
        double relevance = computeDriveRelevance(narrative, drives);

        double urgencyMod = context.weights() != null ? context.weights().urgencyWeight() : 1.0;
        double urgency = Math.min(1.0, relevance * urgencyMod);

        return SecResult.of("relevance",
                SecDimensions.RELEVANCE, relevance,
                SecDimensions.NOVELTY, novelty,
                SecDimensions.URGENCY, urgency);
    }

    private double computeNovelty(String narrative, HabituationState habituation,
                                  HabituationConfig config) {
        if (habituation == null) return 1.0;
        var hash = Integer.toHexString(narrative.hashCode());
        int count = habituation.observationCounts().getOrDefault(hash, 0);
        double rate = config != null ? config.habituationRate() : 0.2;
        return Math.max(0, 1.0 - count * rate);
    }

    private double computeDriveRelevance(String narrative, List<Drive> drives) {
        if (drives.isEmpty()) return 0.0;
        String lower = narrative.toLowerCase();
        double max = 0.0;
        for (var drive : drives) {
            if (lower.contains(drive.name().toLowerCase())) {
                max = Math.max(max, drive.intensity());
            }
            if (!drive.trigger().isEmpty() && lower.contains(drive.trigger().toLowerCase())) {
                max = Math.max(max, drive.intensity() * 0.8);
            }
        }
        return max;
    }
}

package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class EventTriggeredAnalyzer {

    private EventTriggeredAnalyzer() {}

    public static EventImpact analyze(List<Memory> experienceMemories,
                                      List<Memory> affectMemories,
                                      Duration window) {
        if (experienceMemories.isEmpty()) return EventImpact.empty();

        long windowMs = window.toMillis();
        int totalEvents = experienceMemories.size();

        List<double[]> allDeltas = new ArrayList<>();
        Map<String, List<double[]>> deltasByType = new LinkedHashMap<>();

        for (Memory event : experienceMemories) {
            if (event.createdAt() == null) continue;
            Instant t = event.createdAt();
            String type = event.attributes() != null
                          ? event.attributes().getOrDefault(ExperienceAttributeKeys.EVENT_TYPE, "unknown")
                          : "unknown";

            double[] pre = meanPad(affectMemories, t.minusMillis(windowMs), t);
            double[] post = meanPad(affectMemories, t, t.plusMillis(windowMs));

            if (pre == null || post == null) continue;

            double[] delta = {post[0] - pre[0], post[1] - pre[1], post[2] - pre[2]};
            allDeltas.add(delta);
            deltasByType.computeIfAbsent(type, k -> new ArrayList<>()).add(delta);
        }

        int eventCount = allDeltas.size();
        Map<PadDimension, Double> meanDelta = computeMeanDelta(allDeltas);
        Map<PadDimension, ConfidenceInterval> ci = bootstrapCI(allDeltas, 1000, 42L);

        Map<String, EventTypeImpact> byType = new LinkedHashMap<>();
        for (var entry : deltasByType.entrySet()) {
            byType.put(entry.getKey(), new EventTypeImpact(
                    computeMeanDelta(entry.getValue()),
                    bootstrapCI(entry.getValue(), 1000, 42L),
                    entry.getValue().size()));
        }

        return new EventImpact(meanDelta, ci, eventCount, totalEvents, byType);
    }

    private static double[] meanPad(List<Memory> memories, Instant from, Instant to) {
        double sumP = 0, sumA = 0, sumD = 0;
        int count = 0;
        for (Memory m : memories) {
            if (m.createdAt() == null) continue;
            if (!m.createdAt().isBefore(from) && m.createdAt().isBefore(to)) {
                sumP += m.pleasure() != null ? m.pleasure() : 0.0;
                sumA += m.arousal() != null ? m.arousal() : 0.0;
                sumD += m.dominance() != null ? m.dominance() : 0.0;
                count++;
            }
        }
        if (count == 0) return null;
        return new double[]{sumP / count, sumA / count, sumD / count};
    }

    private static Map<PadDimension, Double> computeMeanDelta(List<double[]> deltas) {
        if (deltas.isEmpty()) return Map.of();
        double sumP = 0, sumA = 0, sumD = 0;
        for (double[] d : deltas) { sumP += d[0]; sumA += d[1]; sumD += d[2]; }
        int n = deltas.size();
        return Map.of(PadDimension.PLEASURE, sumP / n,
                      PadDimension.AROUSAL, sumA / n,
                      PadDimension.DOMINANCE, sumD / n);
    }

    private static Map<PadDimension, ConfidenceInterval> bootstrapCI(
            List<double[]> deltas, int resamples, long seed) {
        if (deltas.size() < 2) return Map.of();
        var rng = new Random(seed);
        int n = deltas.size();
        double[] meansP = new double[resamples];
        double[] meansA = new double[resamples];
        double[] meansD = new double[resamples];

        for (int r = 0; r < resamples; r++) {
            double sp = 0, sa = 0, sd = 0;
            for (int i = 0; i < n; i++) {
                double[] d = deltas.get(rng.nextInt(n));
                sp += d[0]; sa += d[1]; sd += d[2];
            }
            meansP[r] = sp / n; meansA[r] = sa / n; meansD[r] = sd / n;
        }

        Arrays.sort(meansP); Arrays.sort(meansA); Arrays.sort(meansD);
        int lo = (int) (resamples * 0.025);
        int hi = (int) (resamples * 0.975);

        return Map.of(
                PadDimension.PLEASURE, new ConfidenceInterval(meansP[lo], meansP[hi]),
                PadDimension.AROUSAL, new ConfidenceInterval(meansA[lo], meansA[hi]),
                PadDimension.DOMINANCE, new ConfidenceInterval(meansD[lo], meansD[hi]));
    }
}

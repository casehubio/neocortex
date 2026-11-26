package io.casehub.neocortex.cognition.drive;

import io.casehub.neocortex.mindmap.intelligence.CuriosityChannelComputer;
import io.casehub.neocortex.mindmap.intelligence.InformationGapDetector;
import io.casehub.neocortex.mindmap.intelligence.InformationGapScore;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

public class CuriosityDrive implements DriveSource {

    private static final int                       TOP_N    = 5;
    private final        InformationGapDetector    gapDetector;
    private final        CuriosityChannelComputer  channelComputer;
    private final        ToDoubleFunction<String>  deprivationBiasFor;
    private final        Supplier<Set<String>>     activeGoalNodeIds;
    private final java.util.concurrent.ConcurrentHashMap<String, List<InformationGapScore>> lastGapsByAgent = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, CuriosityChannel> lastChannelByAgent = new java.util.concurrent.ConcurrentHashMap<>();

    public CuriosityDrive(InformationGapDetector gapDetector,
                          CuriosityChannelComputer channelComputer,
                          ToDoubleFunction<String> deprivationBiasFor,
                          Supplier<Set<String>> activeGoalNodeIds) {
        this.gapDetector        = gapDetector;
        this.channelComputer    = channelComputer;
        this.deprivationBiasFor = deprivationBiasFor;
        this.activeGoalNodeIds  = activeGoalNodeIds;
    }

    public CuriosityDrive(InformationGapDetector gapDetector,
                          CuriosityChannelComputer channelComputer,
                          double deprivationBias,
                          Supplier<Set<String>> activeGoalNodeIds) {
        this(gapDetector, channelComputer, agentId -> deprivationBias, activeGoalNodeIds);
    }

    public CuriosityDrive(InformationGapDetector gapDetector,
                          CuriosityChannelComputer channelComputer,
                          double deprivationBias) {
        this(gapDetector, channelComputer, deprivationBias, Set::of);
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var goals = activeGoalNodeIds.get();
        var gaps = gapDetector.detectGaps(tenantId, goals.isEmpty() ? null : goals);
        lastGapsByAgent.put(agentId, gaps);
        var channel = channelComputer.compute(gaps, deprivationBiasFor.applyAsDouble(agentId));
        if (channel != null) {
            lastChannelByAgent.put(agentId, channel);
        } else {
            lastChannelByAgent.remove(agentId);
        }

        if (gaps.isEmpty()) {
            return new DriveIntensity(DriveAxis.CURIOSITY, 0.0, "no knowledge gaps");
        }

        var topGaps = gaps.stream().limit(TOP_N).toList();
        double intensity = topGaps.stream()
                                  .mapToDouble(InformationGapScore::gapIntensity)
                                  .average().orElse(0.0);
        intensity = Math.clamp(intensity, 0.0, 1.0);

        String trigger = topGaps.stream()
                                .map(this::formatTrigger)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("unknown gaps");

        return new DriveIntensity(DriveAxis.CURIOSITY, intensity, trigger);
    }

    private String formatTrigger(InformationGapScore g) {
        String base = g.nodeName() + " (" + g.gapType().name().toLowerCase().replace('_', ' ') + ")";
        if (g.goalRelevance() >= 0.7) return base + " [goal-relevant]";
        return base;
    }

    public List<InformationGapScore> lastGaps(String agentId) {
        return lastGapsByAgent.getOrDefault(agentId, List.of());
    }

    public List<InformationGapScore> lastGaps() {
        var values = lastGapsByAgent.values();
        return values.isEmpty() ? List.of() : values.iterator().next();
    }

    public CuriosityChannel lastChannel(String agentId) {
        return lastChannelByAgent.get(agentId);
    }

    public CuriosityChannel lastChannel() {
        var values = lastChannelByAgent.values();
        return values.isEmpty() ? null : values.iterator().next();
    }
}

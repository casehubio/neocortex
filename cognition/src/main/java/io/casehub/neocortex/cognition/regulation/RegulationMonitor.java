package io.casehub.neocortex.cognition.regulation;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

public class RegulationMonitor {

    static final int DEFAULT_DECLINE_THRESHOLD = 3;
    static final double BREAKTHROUGH_FLOOR = 0.1;

    private final int declineThreshold;
    private final ConcurrentHashMap<String, TickState> states = new ConcurrentHashMap<>();

    public RegulationMonitor() {
        this(DEFAULT_DECLINE_THRESHOLD);
    }

    public RegulationMonitor(int declineThreshold) {
        this.declineThreshold = declineThreshold;
    }

    public @Nullable StrategyFailureSignal record(String agentId, String tenantId,
                                                    EmotionRegulationResult result) {
        String key = agentId + ":" + tenantId;
        var state = states.compute(key, (k, prev) -> {
            if (prev == null || prev.strategy != result.strategy()) {
                return new TickState(result.strategy(), result.effectiveness(),
                        result.effectiveness(), 0);
            }
            int declines = (result.effectiveness() < prev.lastEffectiveness)
                    ? prev.consecutiveDeclines + 1
                    : 0;
            double initial = (declines > 0) ? prev.initialEffectiveness : result.effectiveness();
            return new TickState(result.strategy(), initial, result.effectiveness(), declines);
        });

        if (result.effectiveness() < BREAKTHROUGH_FLOOR) {
            return new StrategyFailureSignal(result.strategy(),
                    state.consecutiveDeclines, state.initialEffectiveness,
                    result.effectiveness(), true);
        }

        if (state.consecutiveDeclines >= declineThreshold) {
            return new StrategyFailureSignal(result.strategy(),
                    state.consecutiveDeclines, state.initialEffectiveness,
                    result.effectiveness(), false);
        }

        return null;
    }

    public void reset(String agentId, String tenantId) {
        states.remove(agentId + ":" + tenantId);
    }

    private record TickState(
            EmotionRegulationStrategy strategy,
            double initialEffectiveness,
            double lastEffectiveness,
            int consecutiveDeclines
    ) {}
}

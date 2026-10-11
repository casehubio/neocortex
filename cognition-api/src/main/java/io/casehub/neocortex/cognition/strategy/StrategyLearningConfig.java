package io.casehub.neocortex.cognition.strategy;

import io.casehub.neocortex.memory.MemoryDomain;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for metacognitive strategy learning.
 * <p>
 * Controls when and how the agent reflects on interaction patterns to
 * adjust strategy dimensions (verbosity, formality, initiative, etc.).
 * Reflection fires after accumulating enough engagement evidence,
 * producing delta adjustments clamped to prevent overcorrection.
 * <p>
 * Trend thresholds follow reinforcement learning conventions: the
 * improving/declining boundaries are set asymmetrically to be more
 * conservative about concluding improvement (higher threshold) than
 * detecting decline (lower threshold), based on the principle that
 * false-positive improvement claims are more costly than false-negative
 * decline detection.
 * <p>
 * Parameter interactions: the trend thresholds (trendImprovingThreshold,
 * trendDecliningThreshold) partition the [0,1] dimension value space
 * into three zones: improving (above improving threshold), stable
 * (between), declining (below declining threshold). maxDimensionDelta
 * constrains the LLM's per-cycle adjustments, preventing overcorrection.
 * The existing minCasesForReflection gates when reflection fires;
 * maxDimensionDelta gates how far each reflection can move.
 *
 * @see Flavell, J.H. (1979). Metacognition and cognitive monitoring.
 * American Psychologist, 34(10), 906-911.
 */
public record StrategyLearningConfig(
        int minSignalsForConversationCase,
        int minCasesForReflection,
        int maxReflectionSources,
        int maxGuidelines,
        double defaultDimensionValue,
        int maxBufferSize,
        Duration staleStateTimeout,
        MemoryDomain memoryDomain,
        String engagementCaseType,
        String profileCaseType,

        /**
         * Trend score above which the agent's engagement trajectory is
         * classified as IMPROVING, suppressing strategy adjustment.
         *
         * Higher = harder to classify as improving (more conservative);
         * lower = easier to classify as improving.
         * Related: trendDecliningThreshold, defaultDimensionValue.
         *
         * Range: [0.0, 1.0]. Default 0.6.
         */
        double trendImprovingThreshold,

        /**
         * Trend score below which the agent's engagement trajectory is
         * classified as DECLINING, triggering more aggressive adjustment.
         *
         * Higher = more easily classified as declining; lower = harder
         * to classify as declining.
         * Related: trendImprovingThreshold, defaultDimensionValue.
         *
         * Range: [0.0, 1.0]. Default 0.4.
         */
        double trendDecliningThreshold,

        /**
         * Maximum absolute delta per dimension that LLM reflection can
         * recommend. Clamps each dimension's adjustment to prevent
         * overcorrection from a single reflection cycle.
         *
         * Higher = allows bigger swings per reflection (faster adaptation,
         * riskier); lower = slower, stabler adaptation.
         * Related: defaultDimensionValue, minCasesForReflection.
         *
         * Range: [0.01, 0.5]. Default 0.2.
         */
        double maxDimensionDelta
) {

    public StrategyLearningConfig {
        if (minSignalsForConversationCase < 1) {
            throw new IllegalArgumentException("minSignalsForConversationCase must be >= 1");
        }
        if (minCasesForReflection < 1) {throw new IllegalArgumentException("minCasesForReflection must be >= 1");}
        if (maxReflectionSources < 1) {throw new IllegalArgumentException("maxReflectionSources must be >= 1");}
        if (maxGuidelines < 1) {throw new IllegalArgumentException("maxGuidelines must be >= 1");}
        if (maxBufferSize < 1) {throw new IllegalArgumentException("maxBufferSize must be >= 1");}
        if (defaultDimensionValue < 0.0 || defaultDimensionValue > 1.0) {
            throw new IllegalArgumentException("defaultDimensionValue must be in [0,1]");
        }
        if (staleStateTimeout.isNegative() || staleStateTimeout.isZero()) {
            throw new IllegalArgumentException("staleStateTimeout must be positive");
        }
        Objects.requireNonNull(memoryDomain, "memoryDomain required");
        Objects.requireNonNull(engagementCaseType, "engagementCaseType required");
        Objects.requireNonNull(profileCaseType, "profileCaseType required");
    }

    public static StrategyLearningConfig defaults() {
        return new StrategyLearningConfig(
                3, 5, 50, 10, 0.5, 100, Duration.ofHours(24),
                new MemoryDomain("strategy-learning"),
                "engagement-evidence", "strategy-profile",
                0.6, 0.4, 0.2);
    }
}

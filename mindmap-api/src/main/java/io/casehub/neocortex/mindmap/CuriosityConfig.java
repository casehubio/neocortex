package io.casehub.neocortex.mindmap;

import java.util.Map;

/**
 * Configuration for curiosity signal generation in mind map analysis.
 * <p>
 * Controls how structural gaps, quality issues, temporal staleness,
 * centrality patterns, and proximity signals are detected and scored.
 * Curiosity signals drive the agent to explore knowledge gaps and
 * maintain map quality.
 * <p>
 * The signal categories follow Berlyne's epistemic curiosity framework:
 * structural gaps trigger diversive curiosity (exploration), quality
 * issues trigger specific curiosity (verification), and temporal
 * staleness triggers a maintenance drive.
 *
 * @see Berlyne, D.E. (1960). Conflict, Arousal, and Curiosity.
 * McGraw-Hill.
 * @see Litman, J.A. (2005). Curiosity and the pleasures of learning.
 * Cognition & Emotion, 19(6), 793-814.
 */
public record CuriosityConfig(
        double proximityScale,
        long staleDaysThreshold,
        int maxBfsDepth,
        int topCentrality,
        double volatilityThreshold,
        double maxBoostFactor,
        double minDampenFactor,
        double improvingDampenCap,
        double volatilityBoostCap,
        int trajectoryLimit,
        Map<String, Double> categoryWeights,

        /**
         * Subgraph edge density below which a STRUCTURAL curiosity signal
         * fires, indicating a sparse knowledge area worth exploring.
         *
         * Density = edges / (nodes * (nodes-1)). Only fires for subgraphs
         * with more than one node.
         *
         * Range: [0.0, 1.0]. Default 0.1 — very sparse, most real
         * knowledge graphs exceed this.
         */
        double densityThreshold,

        /**
         * Ratio of low-confidence nodes above which a QUALITY curiosity
         * signal fires, indicating a cluster of uncertain knowledge.
         *
         * Range: [0.0, 1.0]. Default 0.5 — majority of nodes must be
         * low-confidence to trigger.
         */
        double lowConfidenceRatio,

        /**
         * Ratio of unvalidated edges above which a QUALITY curiosity
         * signal fires, indicating relationships that need verification.
         *
         * Range: [0.0, 1.0]. Default 0.3 — nearly a third of edges
         * unvalidated triggers the signal.
         */
        double unvalidatedRatio,

        /**
         * Divisor for temporal staleness score computation.
         *
         * score = min(1.0, staleDays / stalenessDivisor). Higher values
         * produce a more gradual ramp from fresh to fully stale.
         *
         * Range: [1.0, 365.0]. Default 180.0 — a node reaches full
         * staleness score after 6 months without update.
         */
        double stalenessDivisor
) {
    public CuriosityConfig {
        categoryWeights = categoryWeights != null ? Map.copyOf(categoryWeights) : Map.of();
    }

    public double categoryWeight(String category) {
        return categoryWeights.getOrDefault(category, 1.0);
    }

    public CuriosityConfig withCategoryWeights(Map<String, Double> categoryWeights) {
        return new CuriosityConfig(proximityScale, staleDaysThreshold, maxBfsDepth, topCentrality,
                                   volatilityThreshold, maxBoostFactor, minDampenFactor, improvingDampenCap,
                                   volatilityBoostCap, trajectoryLimit, categoryWeights,
                                   densityThreshold, lowConfidenceRatio, unvalidatedRatio, stalenessDivisor);
    }

    public static CuriosityConfig defaults() {
        return new CuriosityConfig(7.0, 90, 4, 3, 0.3, 1.0, 0.1, 0.7, 0.5, 20, Map.of(),
                                   0.1, 0.5, 0.3, 180.0);
    }
}

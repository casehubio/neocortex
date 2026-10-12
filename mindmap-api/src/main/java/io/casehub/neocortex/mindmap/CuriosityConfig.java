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
 * Parameters interact in three groups: (1) structural signals use
 * densityThreshold; (2) quality signals use lowConfidenceThreshold,
 * lowConfidenceRatio, and unvalidatedRatio; (3) temporal signals use
 * staleDaysThreshold and stalenessDivisor. Affect dampening parameters
 * (volatilityThreshold, maxBoostFactor, minDampenFactor, improvingDampenCap,
 * volatilityBoostCap, trajectoryLimit) modulate all signal scores based
 * on the node's affective trajectory.
 *
 * @see Berlyne, D.E. (1960). Conflict, Arousal, and Curiosity.
 * McGraw-Hill.
 * @see Litman, J.A. (2005). Curiosity and the pleasures of learning.
 * Cognition & Emotion, 19(6), 793-814.
 */
public record CuriosityConfig(
        /**
         * Temporal decay constant for proximity-based curiosity signals.
         *
         * score = 1 / (1 + daysUntil / proximityScale). Higher values
         * produce a gentler decay — events further in the future still
         * generate moderate signals.
         * Lower = only very imminent events score highly.
         * Related: staleDaysThreshold.
         *
         * Range: [1.0, 30.0]. Default 7.0 — one-week half-life.
         */
        double proximityScale,

        /**
         * Minimum age in days for a node to be considered stale.
         *
         * Nodes updated more recently than this are never flagged as
         * temporal curiosity signals.
         * Higher = more tolerant of old data; lower = aggressive freshness.
         * Related: stalenessDivisor.
         *
         * Range: [1, 365]. Default 90 — three months.
         */
        long staleDaysThreshold,

        /**
         * Maximum BFS depth for topical distance dampening.
         *
         * Signals about nodes farther than this from recent entities
         * are not dampened — they keep full score. Limits the graph
         * traversal cost.
         * Higher = dampens more distant nodes; lower = less dampening.
         *
         * Range: [1, 10]. Default 4.
         */
        int maxBfsDepth,

        /**
         * Maximum centrality signals to emit per subgraph.
         *
         * Caps betweenness and degree centrality signals to avoid
         * flooding the curiosity list with hub nodes.
         * Higher = more centrality signals; lower = only top hubs.
         *
         * Range: [1, 20]. Default 3.
         */
        int topCentrality,

        /**
         * Arousal volatility above which a curiosity boost is applied.
         *
         * Nodes with high affect volatility (emotional instability)
         * receive boosted curiosity scores — they warrant attention.
         * Higher = only extreme volatility triggers boost; lower = more sensitive.
         * Related: volatilityBoostCap.
         *
         * Range: [0.0, 1.0]. Default 0.3.
         */
        double volatilityThreshold,

        /**
         * Maximum multiplicative boost for worsening affect trajectory.
         *
         * Nodes whose pleasure is declining get boosted curiosity.
         * This caps the boost to prevent runaway scores.
         * Higher = stronger boost for distressed nodes.
         * Related: minDampenFactor, improvingDampenCap.
         *
         * Range: [0.0, 5.0]. Default 1.0.
         */
        double maxBoostFactor,

        /**
         * Minimum dampening factor for improving affect trajectory.
         *
         * Nodes whose pleasure is improving get dampened curiosity.
         * This floor prevents complete suppression.
         * Lower = stronger dampening of improving nodes; higher = less dampening.
         * Related: maxBoostFactor, improvingDampenCap.
         *
         * Range: [0.01, 1.0]. Default 0.1.
         */
        double minDampenFactor,

        /**
         * Maximum dampening amount for improving trajectory.
         *
         * Caps how much pleasure slope can reduce curiosity score.
         * Higher = stronger dampening for rapidly improving nodes.
         * Related: minDampenFactor.
         *
         * Range: [0.0, 1.0]. Default 0.7.
         */
        double improvingDampenCap,

        /**
         * Maximum boost from arousal volatility.
         *
         * Caps the volatility-based curiosity boost to prevent
         * emotionally volatile nodes from dominating the signal list.
         * Related: volatilityThreshold.
         *
         * Range: [0.0, 2.0]. Default 0.5.
         */
        double volatilityBoostCap,

        /**
         * Maximum affect memories to retrieve for trajectory analysis.
         *
         * More memories produce more accurate trajectory estimates but
         * increase query cost.
         * Higher = more precise trajectory; lower = faster, noisier.
         *
         * Range: [2, 100]. Default 20.
         */
        int trajectoryLimit,

        Map<String, Double> categoryWeights,

        /**
         * Subgraph edge density below which a STRUCTURAL curiosity signal
         * fires, indicating a sparse knowledge area worth exploring.
         *
         * Density = edges / (nodes * (nodes-1)). Only fires for subgraphs
         * with more than one node.
         * Higher = flags denser subgraphs; lower = only very sparse areas.
         * Related: lowConfidenceRatio, unvalidatedRatio (other quality signals).
         *
         * Range: [0.0, 1.0]. Default 0.1.
         */
        double densityThreshold,

        /**
         * Ratio of low-confidence nodes above which a QUALITY curiosity
         * signal fires, indicating a cluster of uncertain knowledge.
         *
         * Higher = requires more nodes to be low-confidence; lower = more sensitive.
         * Related: lowConfidenceThreshold (defines what counts as "low confidence").
         *
         * Range: [0.0, 1.0]. Default 0.5.
         */
        double lowConfidenceRatio,

        /**
         * Ratio of unvalidated edges above which a QUALITY curiosity
         * signal fires, indicating relationships that need verification.
         *
         * Higher = more tolerant of unvalidated edges; lower = more aggressive.
         * Related: densityThreshold, lowConfidenceRatio.
         *
         * Range: [0.0, 1.0]. Default 0.3.
         */
        double unvalidatedRatio,

        /**
         * Divisor for temporal staleness score computation.
         *
         * score = min(1.0, staleDays / stalenessDivisor). Higher values
         * produce a more gradual ramp from fresh to fully stale.
         * Higher = slower staleness ramp; lower = faster.
         * Related: staleDaysThreshold (minimum age before any staleness).
         *
         * Range: [1.0, 365.0]. Default 180.0 — six months to full score.
         */
        double stalenessDivisor,

        /**
         * Confidence value below which a node is counted as "low confidence"
         * when computing the low-confidence cluster ratio.
         *
         * This is passed to MindMapAnalyzer.lowConfidenceCluster as the
         * threshold parameter.
         * Higher = more nodes classified as low confidence; lower = stricter.
         * Related: lowConfidenceRatio (ratio threshold for signal firing).
         *
         * Range: [0.0, 1.0]. Default 0.5.
         */
        double lowConfidenceThreshold
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
                                   densityThreshold, lowConfidenceRatio, unvalidatedRatio, stalenessDivisor,
                                   lowConfidenceThreshold);
    }

    public static CuriosityConfig defaults() {
        return new CuriosityConfig(7.0, 90, 4, 3, 0.3, 1.0, 0.1, 0.7, 0.5, 20, Map.of(),
                                   0.1, 0.5, 0.3, 180.0, 0.5);
    }
}

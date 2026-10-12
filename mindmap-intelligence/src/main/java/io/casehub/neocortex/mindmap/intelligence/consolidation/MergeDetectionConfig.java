package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for mind map merge detection during consolidation.
 * <p>
 * Merge detection identifies duplicate or near-duplicate nodes in the
 * mind map using a two-signal approach: lexical name similarity
 * (Jaro-Winkler) and structural neighbor overlap (Jaccard index).
 * Candidates above the auto-merge threshold are merged automatically;
 * those in the review zone are flagged for human/agent review.
 * <p>
 * The Jaro-Winkler metric is preferred over edit distance for entity
 * names because it weights prefix matches more heavily — appropriate
 * for natural-language entity names where early characters carry more
 * discriminative power.
 * <p>
 * Parameter interactions: nameWeight + neighborWeight should sum to 1.0.
 * The score thresholds form a cascade: autoMergeThreshold >
 * reviewThreshold > candidateMinScore. nameThreshold pre-filters
 * before scoring; neighborThreshold affects the Jaccard input.
 *
 * @see Jaro, M.A. (1989). Advances in record-linkage methodology as
 * applied to matching the 1985 Census of Tampa, Florida. JASA,
 * 84(406), 414-420.
 * @see Winkler, W.E. (1990). String comparator metrics and enhanced
 * decision rules in the Fellegi-Sunter model of record linkage.
 * Proceedings of the Section on Survey Research Methods, ASA.
 */
@ConfigMapping(prefix = "casehub.mindmap.consolidation.merge")
public interface MergeDetectionConfig {

    /**
     * Minimum Jaro-Winkler similarity between node names to consider
     * them as merge candidates.
     * <p>
     * Higher = stricter name matching; lower = more lenient.
     * Related: nameWeight.
     * <p>
     * Range: [0.0, 1.0]. Default 0.85 — high threshold to avoid
     * false positives from coincidental name similarity.
     */
    @WithDefault("0.85")
    double nameThreshold();

    /**
     * Minimum Jaccard index of neighbor sets to count as structural
     * overlap.
     * <p>
     * Two nodes sharing enough neighbors are likely to represent the
     * same entity. The Jaccard index = |A ∩ B| / |A ∪ B|.
     * <p>
     * Higher = requires more shared neighbors; lower = more lenient.
     * Related: neighborWeight.
     * <p>
     * Range: [0.0, 1.0]. Default 0.3 — relatively low, since even
     * partial neighbor overlap is meaningful.
     */
    @WithDefault("0.3")
    double neighborThreshold();

    /**
     * Combined score above which merges execute automatically without
     * human review.
     * <p>
     * The combined score = nameWeight * nameSimilarity +
     * neighborWeight * neighborOverlap.
     * <p>
     * Higher = fewer auto-merges (safer); lower = more.
     * Related: reviewThreshold, candidateMinScore.
     * <p>
     * Range: [0.0, 1.0]. Default 0.9 — very high confidence required
     * for unattended merges.
     */
    @WithDefault("0.9")
    double autoMergeThreshold();

    /**
     * Combined score above which candidates are flagged for review
     * (below autoMergeThreshold).
     * <p>
     * Candidates scoring between this threshold and autoMergeThreshold
     * are annotated with a mergeCandidate property for later review.
     * <p>
     * Higher = fewer flagged pairs; lower = more.
     * Related: autoMergeThreshold, candidateMinScore.
     * <p>
     * Range: [0.0, 1.0]. Default 0.7.
     */
    @WithDefault("0.7")
    double reviewThreshold();

    /**
     * Weight of name similarity in the combined merge score.
     * <p>
     * combinedScore = nameWeight * nameSim + neighborWeight * neighborOverlap.
     * nameWeight + neighborWeight should typically equal 1.0.
     * <p>
     * Higher = name similarity dominates; lower = neighbor overlap dominates.
     * Related: neighborWeight.
     * <p>
     * Range: [0.0, 1.0]. Default 0.6 — name similarity is the
     * stronger signal for entity deduplication.
     */
    @WithDefault("0.6")
    double nameWeight();

    /**
     * Weight of neighbor overlap in the combined merge score.
     * <p>
     * Higher = neighbor overlap dominates; lower = name similarity dominates.
     * Related: nameWeight.
     * <p>
     * Range: [0.0, 1.0]. Default 0.4.
     */
    @WithDefault("0.4")
    double neighborWeight();

    /**
     * Maximum merges per consolidation pass.
     * <p>
     * Limits the blast radius of a single consolidation cycle.
     * Remaining candidates are picked up in subsequent passes.
     * <p>
     * Higher = more merges per cycle (faster convergence, riskier);
     * lower = more conservative.
     * <p>
     * Range: [1, ∞). Default 10.
     */
    @WithDefault("10")
    int maxPerPass();

    /**
     * Minimum combined score for a pair to be considered a merge candidate.
     * <p>
     * Pairs scoring below this are ignored entirely — not even flagged
     * for review. Acts as a noise floor for the scoring function.
     * <p>
     * Higher = fewer candidates, faster passes; lower = more candidates.
     * Related: reviewThreshold, autoMergeThreshold.
     * <p>
     * Range: [0.0, 1.0]. Default 0.6.
     */
    @WithDefault("0.6")
    double candidateMinScore();
}

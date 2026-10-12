package io.casehub.neocortex.cognition.appraisal;

/**
 * Thresholds for drive activation and concern salience in the appraisal pipeline.
 *
 * Literature grounding:
 * - driveActivationThreshold (0.5): CAPS model (Mischel & Shoda, 1995) — connectionist
 *   nodes fire when activation exceeds threshold. 0.5 represents the midpoint of [0,1],
 *   meaning a drive must be more active than not to be foregrounded.
 * - concernSalienceThreshold (0.1): Klinger's Current Concerns Theory (1971, 2004) —
 *   active concerns make ALL relevant stimuli more salient, not just highly relevant ones.
 *   Low threshold is consistent with broad attentional bias.
 * - tokenMinLength (3): Filters stop words (a, an, in, of, to) from keyword overlap.
 *   Standard NLP preprocessing; not literature-specific.
 */
public record AppraisalThresholds(
        double driveActivationThreshold,
        double concernSalienceThreshold,
        int tokenMinLength) {

    public static AppraisalThresholds defaults() {
        return new AppraisalThresholds(0.5, 0.1, 3);
    }
}

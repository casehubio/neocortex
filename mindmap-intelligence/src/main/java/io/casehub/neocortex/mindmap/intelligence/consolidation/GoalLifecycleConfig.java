package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for goal lifecycle management during consolidation:
 * prioritization weights, decay thresholds, attention signals, and
 * legacy PAD affect mapping.
 *
 * Goal prioritization implements a weighted multi-criteria model
 * combining urgency (temporal pressure), feasibility (resource
 * availability), affective valence (emotional investment), and
 * importance (structural connectivity in the goal network).
 *
 * The weighting scheme follows Goal Setting Theory's finding that
 * goal commitment depends on both importance and self-efficacy
 * (feasibility), with temporal urgency and affective state as
 * modulators.
 *
 * Decay thresholds implement goal disengagement theory: goals with
 * sustained low confidence and negative affect transition through
 * dormancy to abandonment, following Wrosch et al.'s adaptive
 * disengagement model.
 *
 * @see Locke, E.A. & Latham, G.P. (2002). Building a practically
 *      useful theory of goal setting and task motivation. American
 *      Psychologist, 57(9), 705-717.
 * @see Wrosch, C. et al. (2003). Adaptive self-regulation of
 *      unattainable goals. Personality and Social Psychology
 *      Bulletin, 29(12), 1494-1508.
 */
@ConfigMapping(prefix = "casehub.cognition.goals")
public interface GoalLifecycleConfig {

    // ─── Prioritization weights ───────────────────────────────────

    /**
     * Weight for temporal urgency in the priority composite score.
     *
     * Urgency reflects deadline pressure — goals approaching their
     * target date receive higher urgency scores. The four weights
     * (urgency, feasibility, affective, importance) should sum to 1.0.
     *
     * Range: [0.0, 1.0]. Default 0.3 — urgency and importance share
     * top weighting, consistent with Goal Setting Theory's emphasis on
     * goal commitment = importance + deadline salience.
     */
    @WithDefault("0.3")
    double urgencyWeight();

    /**
     * Weight for feasibility in the priority composite score.
     *
     * Feasibility reflects the agent's self-efficacy assessment for
     * achieving the goal. Lower weight than urgency/importance because
     * feasibility changes more slowly than the other dimensions.
     *
     * Range: [0.0, 1.0]. Default 0.2.
     */
    @WithDefault("0.2")
    double feasibilityWeight();

    /**
     * Weight for affective valence in the priority composite score.
     *
     * Affective valence combines pleasure and dominance PAD dimensions
     * to capture emotional investment in the goal.
     *
     * Range: [0.0, 1.0]. Default 0.2.
     */
    @WithDefault("0.2")
    double affectiveWeight();

    /**
     * Weight for structural importance in the priority composite score.
     *
     * Importance is derived from the number of inbound "contributes-to"
     * and "enables" edges — goals that support many other goals rank
     * higher.
     *
     * Range: [0.0, 1.0]. Default 0.3.
     */
    @WithDefault("0.3")
    double importanceWeight();

    // ─── Decay thresholds ─────────────────────────────────────────

    /**
     * Confidence threshold below which an active goal becomes dormant.
     *
     * When confidence drops below this level AND pleasure is non-positive,
     * the goal transitions to dormant status (or signals dormancy for
     * eidos-linked goals). Based on Wrosch et al.'s finding that goal
     * disengagement begins when expectancy of attainment drops below a
     * threshold.
     *
     * Range: [0.0, 1.0]. Default 0.4 — moderate threshold, allowing
     * some uncertainty before triggering disengagement.
     */
    @WithDefault("0.4")
    double dormantConfidenceThreshold();

    /**
     * Confidence threshold below which a goal is abandoned entirely.
     *
     * Requires both low confidence AND negative pleasure — the goal
     * must be both unlikely and aversive. Below this threshold with
     * negative affect, the goal transitions to abandoned.
     *
     * Range: [0.0, 1.0]. Default 0.2 — well below dormancy threshold,
     * ensuring abandonment is a last resort.
     */
    @WithDefault("0.2")
    double abandonConfidenceThreshold();

    // ─── Attention signal thresholds ──────────────────────────────

    /**
     * Urgency level above which an URGENCY_SPIKE attention signal fires.
     *
     * Signals the agent that a goal is approaching a critical deadline.
     * Should be set high enough to avoid alarm fatigue.
     *
     * Range: [0.0, 1.0]. Default 0.7.
     */
    @WithDefault("0.7")
    double urgencySpikeThreshold();

    /**
     * Minimum absolute change in computed priority to fire a
     * PRIORITY_SHIFT attention signal.
     *
     * Prevents noise from minor priority fluctuations between
     * consolidation cycles.
     *
     * Range: [0.0, 1.0]. Default 0.2 — a 20-point shift on the
     * 0-1 scale is significant enough to warrant attention.
     */
    @WithDefault("0.2")
    double priorityShiftThreshold();

    // ─── Legacy PAD affect: urgency delta ─────────────────────────

    /**
     * Minimum absolute change in pleasure to fire an AFFECT_CHANGE
     * signal in the legacy PAD affect path.
     *
     * The legacy path assigns fixed PAD values per goal status rather
     * than using OCC appraisal. This threshold prevents signals when
     * the pleasure dimension barely moves.
     *
     * Range: [0.0, 1.0]. Default 0.3.
     */
    @WithDefault("0.3")
    double legacyAffectDeltaThreshold();
}

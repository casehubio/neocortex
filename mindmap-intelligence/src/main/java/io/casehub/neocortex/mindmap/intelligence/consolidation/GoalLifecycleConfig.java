package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for goal lifecycle management during consolidation:
 * prioritization weights, decay thresholds, attention signals, and
 * legacy PAD affect mapping.
 * <p>
 * Goal prioritization implements a weighted multi-criteria model
 * combining urgency (temporal pressure), feasibility (resource
 * availability), affective valence (emotional investment), and
 * importance (structural connectivity in the goal network).
 * <p>
 * The weighting scheme follows Goal Setting Theory's finding that
 * goal commitment depends on both importance and self-efficacy
 * (feasibility), with temporal urgency and affective state as
 * modulators.
 * <p>
 * Decay thresholds implement goal disengagement theory: goals with
 * sustained low confidence and negative affect transition through
 * dormancy to abandonment, following Wrosch et al.'s adaptive
 * disengagement model.
 * <p>
 * Parameter interactions: the four prioritization weights (urgencyWeight,
 * feasibilityWeight, affectiveWeight, importanceWeight) should sum to 1.0.
 * The decay thresholds form a cascade: dormantConfidenceThreshold >
 * abandonConfidenceThreshold — goals pass through dormancy before
 * abandonment. The per-status PAD values (active*, blocked*, completed*,
 * dormant*, abandoned*) define the legacy affect mapping; when OCC
 * appraisal is available, these are bypassed.
 *
 * @see Locke, E.A. & Latham, G.P. (2002). Building a practically
 * useful theory of goal setting and task motivation. American
 * Psychologist, 57(9), 705-717.
 * @see Wrosch, C. et al. (2003). Adaptive self-regulation of
 * unattainable goals. Personality and Social Psychology
 * Bulletin, 29(12), 1494-1508.
 */
@ConfigMapping(prefix = "casehub.cognition.goals")
public interface GoalLifecycleConfig {

    // ─── Prioritization weights ───────────────────────────────────

    /**
     * Weight for temporal urgency in the priority composite score.
     * <p>
     * Urgency reflects deadline pressure — goals approaching their
     * target date receive higher urgency scores. The four weights
     * (urgency, feasibility, affective, importance) should sum to 1.0.
     * <p>
     * Higher = urgency dominates priority; lower = other factors dominate.
     * Related: feasibilityWeight, affectiveWeight, importanceWeight.
     * <p>
     * Range: [0.0, 1.0]. Default 0.3 — urgency and importance share
     * top weighting, consistent with Goal Setting Theory's emphasis on
     * goal commitment = importance + deadline salience.
     */
    @WithDefault("0.3")
    double urgencyWeight();

    /**
     * Weight for feasibility in the priority composite score.
     * <p>
     * Feasibility reflects the agent's self-efficacy assessment for
     * achieving the goal. Lower weight than urgency/importance because
     * feasibility changes more slowly than the other dimensions.
     * <p>
     * Higher = feasibility dominates; lower = minimized.
     * Related: urgencyWeight, affectiveWeight, importanceWeight.
     * <p>
     * Range: [0.0, 1.0]. Default 0.2.
     */
    @WithDefault("0.2")
    double feasibilityWeight();

    /**
     * Weight for affective valence in the priority composite score.
     * <p>
     * Affective valence combines pleasure and dominance PAD dimensions
     * to capture emotional investment in the goal.
     * <p>
     * Higher = affective state dominates; lower = minimized.
     * Related: urgencyWeight, feasibilityWeight, importanceWeight.
     * <p>
     * Range: [0.0, 1.0]. Default 0.2.
     */
    @WithDefault("0.2")
    double affectiveWeight();

    /**
     * Weight for structural importance in the priority composite score.
     * <p>
     * Importance is derived from the number of inbound "contributes-to"
     * and "enables" edges — goals that support many other goals rank
     * higher.
     * <p>
     * Higher = importance dominates; lower = minimized.
     * Related: urgencyWeight, feasibilityWeight, affectiveWeight.
     * <p>
     * Range: [0.0, 1.0]. Default 0.3.
     */
    @WithDefault("0.3")
    double importanceWeight();

    // ─── Decay thresholds ─────────────────────────────────────────

    /**
     * Confidence threshold below which an active goal becomes dormant.
     * <p>
     * When confidence drops below this level AND pleasure is non-positive,
     * the goal transitions to dormant status (or signals dormancy for
     * eidos-linked goals). Based on Wrosch et al.'s finding that goal
     * disengagement begins when expectancy of attainment drops below a
     * threshold.
     * <p>
     * Higher = goals go dormant sooner; lower = more resilient.
     * Related: abandonConfidenceThreshold.
     * <p>
     * Range: [0.0, 1.0]. Default 0.4 — moderate threshold, allowing
     * some uncertainty before triggering disengagement.
     */
    @WithDefault("0.4")
    double dormantConfidenceThreshold();

    /**
     * Confidence threshold below which a goal is abandoned entirely.
     * <p>
     * Requires both low confidence AND negative pleasure — the goal
     * must be both unlikely and aversive. Below this threshold with
     * negative affect, the goal transitions to abandoned.
     * <p>
     * Higher = goals abandoned sooner; lower = more persistent.
     * Related: dormantConfidenceThreshold.
     * <p>
     * Range: [0.0, 1.0]. Default 0.2 — well below dormancy threshold,
     * ensuring abandonment is a last resort.
     */
    @WithDefault("0.2")
    double abandonConfidenceThreshold();

    // ─── Attention signal thresholds ──────────────────────────────

    /**
     * Urgency level above which an URGENCY_SPIKE attention signal fires.
     * <p>
     * Signals the agent that a goal is approaching a critical deadline.
     * Should be set high enough to avoid alarm fatigue.
     * <p>
     * Higher = fewer spike signals; lower = more sensitive.
     * Related: priorityShiftThreshold.
     * <p>
     * Range: [0.0, 1.0]. Default 0.7.
     */
    @WithDefault("0.7")
    double urgencySpikeThreshold();

    /**
     * Minimum absolute change in computed priority to fire a
     * PRIORITY_SHIFT attention signal.
     * <p>
     * Prevents noise from minor priority fluctuations between
     * consolidation cycles.
     * <p>
     * Higher = fewer shift signals; lower = more sensitive.
     * Related: urgencySpikeThreshold.
     * <p>
     * Range: [0.0, 1.0]. Default 0.2 — a 20-point shift on the
     * 0-1 scale is significant enough to warrant attention.
     */
    @WithDefault("0.2")
    double priorityShiftThreshold();

    // ─── Legacy PAD affect: per-status defaults ──────────────────

    /**
     * Base pleasure value for active goals (before urgency modulation).
     * <p>
     * Active goals have mildly positive affect reflecting ongoing engagement.
     * Higher = more optimistic baseline; lower = more neutral.
     * Related: activeUrgencyArousalFactor, activeDominance.
     * <p>
     * Range: [-1.0, 1.0]. Default 0.2.
     */
    @WithDefault("0.2")
    double activePleasure();

    /**
     * Scaling factor from urgency to arousal for active goals.
     * <p>
     * arousal = urgency * this factor. Higher urgency produces proportionally
     * higher arousal (alertness/activation).
     * Higher = urgency dominates arousal; lower = urgency has little arousal effect.
     * Related: activePleasure, activeDominance.
     * <p>
     * Range: [0.0, 1.0]. Default 0.8.
     */
    @WithDefault("0.8")
    double activeUrgencyArousalFactor();

    /**
     * Dominance value for active goals.
     * <p>
     * Mildly positive dominance reflects a sense of control over the goal.
     * Higher = stronger sense of agency; lower = more neutral/submissive.
     * Related: activePleasure.
     * <p>
     * Range: [-1.0, 1.0]. Default 0.3.
     */
    @WithDefault("0.3")
    double activeDominance();

    /**
     * Base pleasure for blocked goals (before urgency modulation).
     * <p>
     * Blocked goals have negative affect, worsened by urgency.
     * pleasure = this value - (urgency * blockedUrgencyPleasureFactor).
     * More negative = stronger frustration baseline.
     * Related: blockedUrgencyPleasureFactor, blockedDominance.
     * <p>
     * Range: [-1.0, 0.0]. Default -0.4.
     */
    @WithDefault("-0.4")
    double blockedPleasure();

    /**
     * Urgency-modulated pleasure penalty for blocked goals.
     * <p>
     * pleasure = blockedPleasure - (urgency * this factor).
     * Higher = more intense frustration under deadline pressure.
     * Related: blockedPleasure.
     * <p>
     * Range: [0.0, 1.0]. Default 0.4.
     */
    @WithDefault("0.4")
    double blockedUrgencyPleasureFactor();

    /**
     * Base arousal for blocked goals (before urgency modulation).
     * <p>
     * arousal = this value + (urgency * blockedUrgencyArousalFactor).
     * Moderately elevated even without time pressure.
     * Related: blockedUrgencyArousalFactor.
     * <p>
     * Range: [0.0, 1.0]. Default 0.3.
     */
    @WithDefault("0.3")
    double blockedArousal();

    /**
     * Urgency-modulated arousal boost for blocked goals.
     * <p>
     * arousal = blockedArousal + (urgency * this factor).
     * Higher = more agitation under deadline pressure.
     * Related: blockedArousal.
     * <p>
     * Range: [0.0, 1.0]. Default 0.5.
     */
    @WithDefault("0.5")
    double blockedUrgencyArousalFactor();

    /**
     * Dominance for blocked goals. Negative reflects loss of control.
     * <p>
     * Range: [-1.0, 0.0]. Default -0.3.
     */
    @WithDefault("-0.3")
    double blockedDominance();

    /**
     * Pleasure for completed goals. Positive reflects satisfaction.
     * <p>
     * Higher = stronger sense of accomplishment.
     * Related: completedArousal, completedDominance.
     * <p>
     * Range: [0.0, 1.0]. Default 0.6.
     */
    @WithDefault("0.6")
    double completedPleasure();

    /**
     * Arousal for completed goals. Mildly positive (relief, not excitement).
     * <p>
     * Range: [-1.0, 1.0]. Default 0.2.
     */
    @WithDefault("0.2")
    double completedArousal();

    /**
     * Dominance for completed goals. Positive reflects mastery.
     * <p>
     * Range: [-1.0, 1.0]. Default 0.5.
     */
    @WithDefault("0.5")
    double completedDominance();

    /**
     * Pleasure for dormant goals. Neutral — goal is shelved, not lost.
     * <p>
     * Range: [-1.0, 1.0]. Default 0.0.
     */
    @WithDefault("0.0")
    double dormantPleasure();

    /**
     * Arousal for dormant goals. Negative reflects low activation.
     * <p>
     * Range: [-1.0, 0.0]. Default -0.3.
     */
    @WithDefault("-0.3")
    double dormantArousal();

    /**
     * Dominance for dormant goals. Neutral.
     * <p>
     * Range: [-1.0, 1.0]. Default 0.0.
     */
    @WithDefault("0.0")
    double dormantDominance();

    /**
     * Pleasure for abandoned goals. Mildly negative — regret, not distress.
     * <p>
     * Range: [-1.0, 0.0]. Default -0.2.
     */
    @WithDefault("-0.2")
    double abandonedPleasure();

    /**
     * Arousal for abandoned goals. Low — disengagement.
     * <p>
     * Range: [-1.0, 0.0]. Default -0.2.
     */
    @WithDefault("-0.2")
    double abandonedArousal();

    /**
     * Dominance for abandoned goals. Mildly negative — loss of agency.
     * <p>
     * Range: [-1.0, 0.0]. Default -0.1.
     */
    @WithDefault("-0.1")
    double abandonedDominance();

    // ─── Legacy PAD affect: urgency delta ─────────────────────────

    /**
     * Minimum absolute change in pleasure to fire an AFFECT_CHANGE
     * signal in the legacy PAD affect path.
     * <p>
     * The legacy path assigns fixed PAD values per goal status rather
     * than using OCC appraisal. This threshold prevents signals when
     * the pleasure dimension barely moves.
     * <p>
     * Higher = fewer affect signals; lower = more sensitive.
     * <p>
     * Range: [0.0, 1.0]. Default 0.3.
     */
    @WithDefault("0.3")
    double legacyAffectDeltaThreshold();
}

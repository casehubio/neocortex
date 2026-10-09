package io.casehub.neocortex.cognition.prompt;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for cognitive prompt rendering thresholds.
 *
 * Controls how behavioral attractors, appraisal emotions, action
 * tendencies, and mood states are mapped to natural-language
 * descriptions in the agent's system prompt. These thresholds
 * determine what is visible to the LLM and how intensities are
 * labeled.
 *
 * The three-tier labeling scheme (strong / moderate / mild) follows
 * Russell's circumplex model of affect, where emotional intensity
 * maps to distance from the origin in PAD space. Threshold values
 * are set to produce psychologically plausible label distributions.
 *
 * @see Russell, J.A. (1980). A circumplex model of affect. Journal
 *      of Personality and Social Psychology, 39(6), 1161-1178.
 * @see Mehrabian, A. (1996). Pleasure-arousal-dominance: A general
 *      framework for describing and measuring individual differences
 *      in temperament. Current Psychology, 14, 261-292.
 */
@ConfigMapping(prefix = "casehub.cognition.prompt")
public interface PromptRenderingConfig {

    // ─── Behavioral attractor rendering ───────────────────────────

    /**
     * Minimum attractor strength to include in prompt rendering.
     *
     * Attractors below this threshold are not mentioned in the
     * behavioral prompt section. Filters noise from very weak
     * behavioral tendencies.
     *
     * Range: [0.0, 0.5]. Default 0.1.
     */
    @WithDefault("0.1")
    double behavioralMinStrength();

    /**
     * Attractor strength above which the label is "deeply ingrained".
     *
     * Range: [0.5, 1.0]. Default 0.7.
     */
    @WithDefault("0.7")
    double behavioralDeepRootedThreshold();

    /**
     * Attractor strength above which the label is "noticeable"
     * (below deepRooted threshold).
     *
     * Strengths between this and behavioralMinStrength are labeled
     * "emerging".
     *
     * Range: [0.1, 0.7]. Default 0.4.
     */
    @WithDefault("0.4")
    double behavioralNoticeableThreshold();

    /**
     * Minimum change in attractor strength between cycles to report
     * a trend ("strengthening" or "fading").
     *
     * Range: [0.01, 0.2]. Default 0.05.
     */
    @WithDefault("0.05")
    double behavioralTrendDelta();

    // ─── Appraisal emotion rendering ──────────────────────────────

    /**
     * Minimum action tendency intensity to include in prompt.
     *
     * Action tendencies (approach, avoidance, attending, etc.) below
     * this threshold are not rendered. Prevents low-intensity
     * tendencies from cluttering the prompt.
     *
     * Range: [0.0, 1.0]. Default 0.3.
     */
    @WithDefault("0.3")
    double actionTendencyMinIntensity();

    /**
     * Emotion intensity above which the label prefix is "strong".
     *
     * Range: [0.5, 1.0]. Default 0.7.
     */
    @WithDefault("0.7")
    double emotionStrongThreshold();

    /**
     * Emotion intensity above which the label has no prefix (moderate).
     * Below this threshold the prefix is "mild".
     *
     * Range: [0.1, 0.7]. Default 0.4.
     */
    @WithDefault("0.4")
    double emotionModerateThreshold();

    // ─── Mood rendering ───────────────────────────────────────────

    /**
     * PAD dimension magnitude above which a mood label or behavioral
     * coloring cue is generated.
     *
     * Each PAD dimension (pleasure, arousal, dominance) is compared
     * against this threshold. Values within [-threshold, +threshold]
     * are treated as neutral on that dimension.
     *
     * Based on Mehrabian's PAD temperament model where the neutral
     * zone represents the resting baseline — only deviations beyond
     * this range produce perceptible mood effects.
     *
     * Range: [0.1, 0.5]. Default 0.3.
     */
    @WithDefault("0.3")
    double moodPadThreshold();
}

package io.casehub.neocortex.cognition.appraisal;

/**
 * Weights for the four expression channels grounded in Barrett (2017)
 * constructed emotion: somatic markers, attentional focus, temporal
 * experience, and action tendency (Frijda 1986).
 *
 * <p>Situation-dependent weighting per Easterbrook (1959): emotional
 * arousal narrows the attentional field, shifting emphasis toward
 * somatic and action tendency channels under threat.
 */
public record ChannelWeightProfile(
        double somatic,
        double attentional,
        double temporal,
        double actionTendency
) {
    public static final ChannelWeightProfile DEFAULT =
            new ChannelWeightProfile(0.25, 0.25, 0.25, 0.25);

    public ChannelWeightProfile {
        if (somatic < 0 || attentional < 0 || temporal < 0 || actionTendency < 0) {
            throw new IllegalArgumentException("Channel weights must be non-negative");
        }
        double sum = somatic + attentional + temporal + actionTendency;
        if (Math.abs(sum - 1.0) > 0.01) {
            throw new IllegalArgumentException(
                    "Channel weights must sum to 1.0 (got " + sum + ")");
        }
    }

    public String dominantChannel() {
        double max = Math.max(Math.max(somatic, attentional),
                              Math.max(temporal, actionTendency));
        if (max == somatic) return "somatic";
        if (max == attentional) return "attentional";
        if (max == temporal) return "temporal";
        return "action-tendency";
    }

    public boolean isDefault() {
        return Math.abs(somatic - 0.25) < 0.01
            && Math.abs(attentional - 0.25) < 0.01
            && Math.abs(temporal - 0.25) < 0.01
            && Math.abs(actionTendency - 0.25) < 0.01;
    }

    /**
     * Blend two profiles by a mixing factor (0.0 = this, 1.0 = other).
     */
    public ChannelWeightProfile blend(ChannelWeightProfile other, double factor) {
        double f = Math.max(0, Math.min(1, factor));
        double inv = 1.0 - f;
        return new ChannelWeightProfile(
                somatic * inv + other.somatic * f,
                attentional * inv + other.attentional * f,
                temporal * inv + other.temporal * f,
                actionTendency * inv + other.actionTendency * f
        );
    }
}

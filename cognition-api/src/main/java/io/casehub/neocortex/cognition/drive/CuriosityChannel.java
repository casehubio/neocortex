package io.casehub.neocortex.cognition.drive;

/**
 * Four-channel curiosity state per Litman (2005), Silvia (2006), Berlyne (1960),
 * and Loewenstein (1994). Channels are orthogonal dimensions of the curiosity
 * experience — the same intensity feels different depending on channel composition.
 *
 * @param hedonic       Litman I/D: pleasurable interest vs aversive need-to-know
 * @param comprehension Silvia appraisal: how well the agent is grasping (0=confused, 1=clear)
 * @param focus         Berlyne: attention locked on one question vs scanning broadly
 * @param gapProximity  Loewenstein: non-linear — peak at partial knowledge (~0.5 confidence)
 */
public record CuriosityChannel(
        HedonicType hedonic,
        double comprehension,
        FocusType focus,
        double gapProximity) {

    /** Litman (2005) Interest/Deprivation model. */
    public enum HedonicType {
        /** Pleasurable anticipation — exploration is the reward (openness-correlated). */
        I_TYPE,
        /** Aversive need-to-know — the gap is uncomfortable (neuroticism-correlated). */
        D_TYPE,
        /** Both present — high openness + moderate neuroticism. */
        BLENDED
    }

    /** Berlyne (1960) specific vs diversive curiosity. */
    public enum FocusType {
        /** Attention locked on one question or topic. */
        SPECIFIC,
        /** Broad scanning — everything seems worth exploring. */
        DIVERSIVE
    }

    public CuriosityChannel {
        if (comprehension < 0.0 || comprehension > 1.0)
            throw new IllegalArgumentException("comprehension must be in [0,1], got " + comprehension);
        if (gapProximity < 0.0 || gapProximity > 1.0)
            throw new IllegalArgumentException("gapProximity must be in [0,1], got " + gapProximity);
    }
}

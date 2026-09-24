package io.casehub.neocortex.cognitive;

public record PadProjection(double pleasure, double arousal, double dominance) {
    public static final PadProjection NEUTRAL = new PadProjection(0, 0, 0);
}

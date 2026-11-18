package io.casehub.neocortex.mindmap.intelligence.tom;

public record TomPersonalityModulation(
        double anxiousness,
        double callousness,
        double suspiciousness
) {
    public static final TomPersonalityModulation NEUTRAL = new TomPersonalityModulation(0.5, 0.0, 0.0);

    public TomPersonalityModulation {
        if (anxiousness < 0.0 || anxiousness > 1.0) {
            throw new IllegalArgumentException("anxiousness must be in [0,1], got " + anxiousness);
        }
        if (callousness < 0.0 || callousness > 1.0) {
            throw new IllegalArgumentException("callousness must be in [0,1], got " + callousness);
        }
        if (suspiciousness < 0.0 || suspiciousness > 1.0) {
            throw new IllegalArgumentException("suspiciousness must be in [0,1], got " + suspiciousness);
        }
    }
}

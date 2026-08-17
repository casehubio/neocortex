package io.casehub.neocortex.memory.mood;

public record MoodBaseline(double pleasure, double arousal, double dominance) {
    public MoodBaseline {
        validateAxis("pleasure", pleasure);
        validateAxis("arousal", arousal);
        validateAxis("dominance", dominance);
    }

    private static void validateAxis(String name, double value) {
        if (value < -1.0 || value > 1.0)
            throw new IllegalArgumentException(name + " must be in [-1, 1], got " + value);
    }
}

package io.casehub.neocortex.cognition.belief;

public record BeliefRevisionConfig(
    boolean enabled,
    double beliefDecayPerContradiction,
    double beliefSupersessionThreshold,
    double revisedBeliefInitialConfidence
) {
    public static BeliefRevisionConfig defaults() {
        return new BeliefRevisionConfig(true, 0.15, 0.3, 0.6);
    }
}

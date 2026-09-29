package io.casehub.neocortex.cognition.belief;

public record BeliefRevisionConfig(
    double beliefDecayPerContradiction,
    double beliefSupersessionThreshold,
    double revisedBeliefInitialConfidence
) {
    public static BeliefRevisionConfig defaults() {
        return new BeliefRevisionConfig(0.15, 0.3, 0.6);
    }
}

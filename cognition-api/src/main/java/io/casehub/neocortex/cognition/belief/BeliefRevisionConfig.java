package io.casehub.neocortex.cognition.belief;

public record BeliefRevisionConfig(
        boolean enabled,
        double beliefDecayPerContradiction,
        double beliefSupersessionThreshold,
        double revisedBeliefInitialConfidence,
        double beliefReinforcementStep
) {
    public BeliefRevisionConfig(boolean enabled, double beliefDecayPerContradiction,
                                double beliefSupersessionThreshold, double revisedBeliefInitialConfidence) {
        this(enabled, beliefDecayPerContradiction, beliefSupersessionThreshold,
             revisedBeliefInitialConfidence, 0.05);
    }

    public static BeliefRevisionConfig defaults() {
        return new BeliefRevisionConfig(true, 0.15, 0.3, 0.6, 0.05);
    }
}

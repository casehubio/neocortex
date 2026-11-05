package io.casehub.neocortex.cognition.regulation;

public record StrategyFailureSignal(
        EmotionRegulationStrategy failingStrategy,
        int consecutiveDeclines,
        double initialEffectiveness,
        double currentEffectiveness,
        boolean breakthrough
) {}

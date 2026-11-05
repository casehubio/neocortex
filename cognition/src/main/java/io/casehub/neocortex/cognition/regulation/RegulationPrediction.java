package io.casehub.neocortex.cognition.regulation;

// Petter et al. 2025 — personality-predicted strategy with confidence score
public record RegulationPrediction(
    EmotionRegulationStrategy strategy,
    double confidence,
    double predictedEffectiveness
) {}

package io.casehub.neocortex.cognition.journal;

import java.util.Map;

public record CapsSettlingResult(Map<String, Double> activations, int iterations,
                                  String convergenceType, double saturationRatio) {}

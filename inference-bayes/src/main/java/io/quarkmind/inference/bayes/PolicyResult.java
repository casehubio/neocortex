package io.quarkmind.inference.bayes;

import java.util.Map;

public record PolicyResult(
    String decisionVariable,
    Map<Map<String, String>, String> optimalPolicy,
    double maxExpectedUtility,
    Map<String, Double> actionUtilities
) {}

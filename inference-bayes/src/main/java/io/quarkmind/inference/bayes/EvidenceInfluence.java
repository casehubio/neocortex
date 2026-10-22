package io.quarkmind.inference.bayes;

import java.util.List;

public record EvidenceInfluence(
    String variableName,
    double influence,
    Direction direction,
    List<String> messagePath,
    boolean confounded,
    double causalEffect
) {}

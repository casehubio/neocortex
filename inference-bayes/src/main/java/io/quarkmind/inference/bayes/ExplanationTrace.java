package io.quarkmind.inference.bayes;

import java.util.List;

public record ExplanationTrace(
    String targetVar,
    double[] priorDistribution,
    double[] posteriorDistribution,
    String[] targetOutcomes,
    List<EvidenceInfluence> influences
) {}

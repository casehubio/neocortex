package io.quarkmind.inference.bayes;

import java.util.List;
import java.util.Set;

public record InfluenceDiagram(
    BayesNetwork network,
    Set<String> decisionNodeNames,
    List<UtilityNode> utilityNodes
) {}

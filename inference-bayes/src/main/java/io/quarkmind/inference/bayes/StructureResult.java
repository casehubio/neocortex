package io.quarkmind.inference.bayes;

import java.util.List;
import java.util.Map;

public record StructureResult(
    NetworkSpec networkSpec,
    String algorithm,
    StructureDiagnostics diagnostics
) {
    public BayesNetwork toBayesNetwork(List<Map<String, String>> data) {
        return ParameterLearner.fit(networkSpec.toBayesNetwork(), data);
    }

    public BayesNetwork toBayesNetwork(List<Map<String, String>> data, double alpha) {
        return ParameterLearner.fit(networkSpec.toBayesNetwork(), data, alpha);
    }
}

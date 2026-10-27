package io.quarkmind.inference.bayes;

import java.util.List;
import java.util.Map;

public final class StructureLearner {

    private StructureLearner() {}

    public static StructureResult pc(List<Map<String, String>> data,
                                      IndependenceTest test,
                                      StructuralConstraints constraints) {
        return PcAlgorithm.learn(data, test, constraints);
    }

    public static StructureResult pc(List<Map<String, String>> data,
                                      double alpha) {
        return PcAlgorithm.learn(data, IndependenceTest.adaptive(alpha),
            StructuralConstraints.none());
    }

    public static StructureResult hillClimbing(
            List<Map<String, String>> data,
            StructuralConstraints constraints,
            int restarts, long seed) {
        return HillClimbingSearch.learn(data, constraints, restarts, seed);
    }

    public static StructureResult hillClimbing(List<Map<String, String>> data) {
        return HillClimbingSearch.learn(data, StructuralConstraints.none(),
            5, System.nanoTime());
    }
}

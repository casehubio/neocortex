package io.quarkmind.inference.bayes;

import java.util.List;

public sealed interface StructureDiagnostics {

    record PcDiagnostics(
        int totalTests,
        int edgesRemoved,
        int vStructuresFound,
        int edgesOriented,
        int edgesUndirected,
        int maxConditioningSetSize
    ) implements StructureDiagnostics {}

    record HillClimbingDiagnostics(
        double bestScore,
        int totalOperations,
        int restartCount,
        int bestRestartIndex,
        List<Double> restartScores
    ) implements StructureDiagnostics {
        public HillClimbingDiagnostics {
            restartScores = List.copyOf(restartScores);
        }
    }
}

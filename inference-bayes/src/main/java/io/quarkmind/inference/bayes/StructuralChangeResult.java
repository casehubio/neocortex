package io.quarkmind.inference.bayes;

import java.util.Set;

public record StructuralChangeResult(
    ChangeType changeType,
    Set<String> affectedVariables,
    int evidenceRestored,
    int evidenceLost,
    long rebuildNanos
) {
    public enum ChangeType {
        INCREMENTAL_LEAF,
        INCREMENTAL_EDGE_REMOVAL,
        REBUILD
    }
}

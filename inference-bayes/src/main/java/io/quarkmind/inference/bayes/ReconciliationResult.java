package io.quarkmind.inference.bayes;

import java.util.List;

public record ReconciliationResult(
    NetworkSpec mergedSpec,
    List<EdgeResolution> edges,
    List<StructuralConflict> conflicts
) {
    public ReconciliationResult {
        edges = List.copyOf(edges);
        conflicts = List.copyOf(conflicts);
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
}

package io.casehub.neocortex.corpus.management;

import java.util.List;
import java.util.Objects;

public record ChangeResult(List<ChangeRecord> changes, int count) {

    public ChangeResult {
        Objects.requireNonNull(changes, "changes");
        changes = List.copyOf(changes);
    }

    public ChangeResult(List<ChangeRecord> changes) {
        this(changes, changes.size());
    }
}

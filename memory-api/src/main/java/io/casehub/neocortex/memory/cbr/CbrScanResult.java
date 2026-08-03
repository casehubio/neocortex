package io.casehub.neocortex.memory.cbr;

import java.util.List;
import java.util.Objects;

public record CbrScanResult(List<CbrCaseSummary> items, String nextCursor) {
    public CbrScanResult {
        Objects.requireNonNull(items, "items required");
        items = List.copyOf(items);
    }

    public boolean hasMore() {
        return nextCursor != null;
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}

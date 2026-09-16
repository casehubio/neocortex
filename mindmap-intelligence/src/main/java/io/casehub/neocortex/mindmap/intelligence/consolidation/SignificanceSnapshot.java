package io.casehub.neocortex.mindmap.intelligence.consolidation;

import java.util.Map;

public record SignificanceSnapshot(Map<String, Double> perTenant) {
    public SignificanceSnapshot {
        perTenant = Map.copyOf(perTenant);
    }

    public static final SignificanceSnapshot EMPTY = new SignificanceSnapshot(Map.of());
}

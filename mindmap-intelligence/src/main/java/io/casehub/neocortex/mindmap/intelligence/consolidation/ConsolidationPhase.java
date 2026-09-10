package io.casehub.neocortex.mindmap.intelligence.consolidation;

import java.util.List;

public interface ConsolidationPhase {
    String name();
    void run(String tenantId, List<String> subgraphPriority);
}

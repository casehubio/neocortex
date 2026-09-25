package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.AttentionSignal;

import java.util.List;

public interface ConsolidationPhase {
    String name();
    void run(String tenantId, List<String> subgraphPriority);

    default void beginTick() {}

    default List<AttentionSignal> signals() {return List.of();}


}

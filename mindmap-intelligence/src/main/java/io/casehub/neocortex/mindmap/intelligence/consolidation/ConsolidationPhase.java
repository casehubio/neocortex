package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.ConsolidationArtifact;

import java.util.List;

public interface ConsolidationPhase {
    String name();
    void run(String tenantId, List<String> subgraphPriority);

    default void beginTick() {}

    default void setSessionPipeline(io.casehub.platform.agent.session.SessionPipeline pipeline) {}


    default List<AttentionSignal> signals() {return List.of();}

    default List<ConsolidationArtifact> artifacts() {return List.of();}
}

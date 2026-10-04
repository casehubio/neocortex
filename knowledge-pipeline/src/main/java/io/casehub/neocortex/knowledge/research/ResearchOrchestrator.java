package io.casehub.neocortex.knowledge.research;

import io.casehub.neocortex.knowledge.ResearchSession;
import io.casehub.neocortex.knowledge.ResearchSessionService;
import io.casehub.neocortex.knowledge.ResearchState;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ResearchOrchestrator implements ResearchSessionService {

    private final ResearchSessionStore sessionStore;
    private final MindMapStore mindMapStore;

    public ResearchOrchestrator(ResearchSessionStore sessionStore,
                                 MindMapStore mindMapStore) {
        this.sessionStore = sessionStore;
        this.mindMapStore = mindMapStore;
    }

    @Override
    public ResearchSession create(String name, String criteria, String tenantId) {
        String subgraphId = mindMapStore.createSubgraph(
            new SubgraphInput(name, SubgraphTypes.RESEARCH_AREA, null), tenantId);

        String rootNodeId = mindMapStore.addNode(
            NodeInput.of(name, subgraphId).withProvenance("knowledge-pipeline"),
            tenantId);

        mindMapStore.updateSubgraph(subgraphId, rootNodeId, tenantId);

        Instant now = Instant.now();
        ResearchSession session = new ResearchSession(
            UUID.randomUUID().toString(), name, criteria, subgraphId,
            ResearchState.ACTIVE, tenantId, now, now);
        sessionStore.insert(session);
        return session;
    }

    @Override
    public void pause(String sessionId) {
        sessionStore.updateState(sessionId, ResearchState.PAUSED, Instant.now());
    }

    @Override
    public void resume(String sessionId) {
        sessionStore.updateState(sessionId, ResearchState.ACTIVE, Instant.now());
    }

    @Override
    public void complete(String sessionId) {
        sessionStore.updateState(sessionId, ResearchState.COMPLETED, Instant.now());
    }

    @Override
    public List<ResearchSession> listActive(String tenantId) {
        return sessionStore.listByState(tenantId, ResearchState.ACTIVE);
    }

    @Override
    public ResearchSession get(String sessionId) {
        return sessionStore.get(sessionId).orElse(null);
    }
}

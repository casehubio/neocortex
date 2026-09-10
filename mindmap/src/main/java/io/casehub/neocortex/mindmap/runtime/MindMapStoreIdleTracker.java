package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.*;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.Set;

@Decorator
@Priority(30)
public class MindMapStoreIdleTracker extends AbstractForwardingMindMapStore {

    private final IdleTracker idleTracker;

    @Inject
    public MindMapStoreIdleTracker(@Delegate @Any MindMapStore delegate,
                                    IdleTracker idleTracker) {
        super(delegate);
        this.idleTracker = idleTracker;
    }

    @Override
    public String addNode(NodeInput input, String tenantId) {
        idleTracker.recordWrite();
        return delegate().addNode(input, tenantId);
    }

    @Override
    public void updateNode(String nodeId, NodeUpdate update, String tenantId) {
        idleTracker.recordWrite();
        delegate().updateNode(nodeId, update, tenantId);
    }

    @Override
    public String addEdge(EdgeInput input, String tenantId) {
        idleTracker.recordWrite();
        return delegate().addEdge(input, tenantId);
    }

    @Override
    public void removeEdge(String edgeId, String tenantId) {
        idleTracker.recordWrite();
        delegate().removeEdge(edgeId, tenantId);
    }

    @Override
    public MergeResult mergeNodes(String keepNodeId, String removeNodeId, String tenantId) {
        idleTracker.recordWrite();
        return delegate().mergeNodes(keepNodeId, removeNodeId, tenantId);
    }

    @Override
    public void supersede(String targetId, String supersedingId, String reason, String tenantId) {
        idleTracker.recordWrite();
        delegate().supersede(targetId, supersedingId, reason, tenantId);
    }

    @Override
    public void reinstate(String targetId, String tenantId) {
        idleTracker.recordWrite();
        delegate().reinstate(targetId, tenantId);
    }

    @Override
    public int eraseNode(String nodeId, String tenantId) {
        idleTracker.recordWrite();
        return delegate().eraseNode(nodeId, tenantId);
    }

    @Override
    public int eraseSubgraph(String subgraphId, String tenantId) {
        idleTracker.recordWrite();
        return delegate().eraseSubgraph(subgraphId, tenantId);
    }

    @Override
    public int eraseEntity(String entityName, String tenantId) {
        idleTracker.recordWrite();
        return delegate().eraseEntity(entityName, tenantId);
    }

    @Override
    public int eraseEntityAcrossTenants(String entityName, Set<String> tenantIds) {
        idleTracker.recordWrite();
        return delegate().eraseEntityAcrossTenants(entityName, tenantIds);
    }

    @Override
    public String createSubgraph(SubgraphInput input, String tenantId) {
        idleTracker.recordWrite();
        return delegate().createSubgraph(input, tenantId);
    }

    @Override
    public void updateSubgraph(String subgraphId, String rootNodeId, String tenantId) {
        idleTracker.recordWrite();
        delegate().updateSubgraph(subgraphId, rootNodeId, tenantId);
    }

    @Override
    public void addAlias(String nodeId, String alias, String tenantId) {
        idleTracker.recordWrite();
        delegate().addAlias(nodeId, alias, tenantId);
    }

    @Override
    public void removeAlias(String nodeId, String alias, String tenantId) {
        idleTracker.recordWrite();
        delegate().removeAlias(nodeId, alias, tenantId);
    }
}

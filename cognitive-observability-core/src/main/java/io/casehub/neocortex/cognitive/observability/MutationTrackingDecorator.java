package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.mindmap.AbstractForwardingMindMapStore;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MergeResult;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MutationContext;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class MutationTrackingDecorator extends AbstractForwardingMindMapStore {

    private final SnapshotStore snapshotStore;
    private final Consumer<GraphMutationRecorded> eventSink;

    public MutationTrackingDecorator(MindMapStore delegate,
                                      SnapshotStore snapshotStore,
                                      Consumer<GraphMutationRecorded> eventSink) {
        super(delegate);
        this.snapshotStore = snapshotStore;
        this.eventSink = eventSink;
    }

    @Override
    public String addNode(NodeInput input, String tenantId) {
        String nodeId = delegate().addNode(input, tenantId);
        persistMutation(new GraphMutation.NodeAdded(
            nodeId, input.name(), input.subgraphId(),
            input.confidence(), Instant.now(), MutationContext.get()), tenantId);
        return nodeId;
    }

    @Override
    public void updateNode(String nodeId, NodeUpdate update, String tenantId) {
        MindMapNode live = delegate().getNode(nodeId, tenantId);
        NodeSnapshot before = live != null ? NodeSnapshot.from(live) : null;
        String subgraphId = live != null ? live.subgraphId() : null;
        delegate().updateNode(nodeId, update, tenantId);
        Map<String, FieldChange> changes = FieldChange.diffSnapshot(before, update);
        if (!changes.isEmpty()) {
            persistMutation(new GraphMutation.NodeUpdated(
                nodeId, subgraphId, changes, Instant.now(), MutationContext.get()), tenantId);
        }
    }

    @Override
    public String addEdge(EdgeInput input, String tenantId) {
        String edgeId = delegate().addEdge(input, tenantId);
        persistMutation(new GraphMutation.EdgeAdded(
            edgeId, input.sourceNodeId(), input.targetNodeId(),
            input.edgeType(), input.confidence(),
            Instant.now(), MutationContext.get()), tenantId);
        return edgeId;
    }

    @Override
    public List<String> addNodes(List<NodeInput> inputs, String tenantId) {
        List<String> nodeIds = delegate().addNodes(inputs, tenantId);
        Instant      now     = Instant.now();
        for (int i = 0; i < nodeIds.size(); i++) {
            NodeInput input = inputs.get(i);
            persistMutation(new GraphMutation.NodeAdded(
                    nodeIds.get(i), input.name(), input.subgraphId(),
                    input.confidence(), now, MutationContext.get()), tenantId);
        }
        return nodeIds;
    }

    @Override
    public List<String> addEdges(List<EdgeInput> inputs, String tenantId) {
        List<String> edgeIds = delegate().addEdges(inputs, tenantId);
        Instant      now     = Instant.now();
        for (int i = 0; i < edgeIds.size(); i++) {
            EdgeInput input = inputs.get(i);
            persistMutation(new GraphMutation.EdgeAdded(
                    edgeIds.get(i), input.sourceNodeId(), input.targetNodeId(),
                    input.edgeType(), input.confidence(), now, MutationContext.get()), tenantId);
        }
        return edgeIds;
    }


    @Override
    public void removeEdge(String edgeId, String tenantId) {
        MindMapEdge edge = delegate().getEdge(edgeId, tenantId);
        delegate().removeEdge(edgeId, tenantId);
        if (edge != null) {
            persistMutation(new GraphMutation.EdgeRemoved(
                edgeId, edge.sourceNodeId(), edge.targetNodeId(),
                edge.edgeType(), Instant.now(), MutationContext.get()), tenantId);
        }
    }

    @Override
    public int eraseNode(String nodeId, String tenantId) {
        MindMapNode node = delegate().getNode(nodeId, tenantId);
        int affected = delegate().eraseNode(nodeId, tenantId);
        String subgraphId = node != null ? node.subgraphId() : null;
        persistMutation(new GraphMutation.NodeErased(
            nodeId, subgraphId, Math.max(0, affected - 1),
            Instant.now(), MutationContext.get()), tenantId);
        return affected;
    }

    @Override
    public MergeResult mergeNodes(String keepNodeId, String removeNodeId, String tenantId) {
        MergeResult result = delegate().mergeNodes(keepNodeId, removeNodeId, tenantId);
        persistMutation(new GraphMutation.NodesMerged(
            result.survivingNodeId(), removeNodeId,
            result.propertyConflicts(),
            Instant.now(), MutationContext.get()), tenantId);
        return result;
    }

    @Override
    public void supersede(String targetId, String supersedingId, String reason, String tenantId) {
        delegate().supersede(targetId, supersedingId, reason, tenantId);
        persistMutation(new GraphMutation.NodeSuperseded(
            targetId, supersedingId, reason,
            Instant.now(), MutationContext.get()), tenantId);
    }

    @Override
    public void reinstate(String targetId, String tenantId) {
        delegate().reinstate(targetId, tenantId);
        persistMutation(new GraphMutation.NodeReinstated(
            targetId, Instant.now(), MutationContext.get()), tenantId);
    }

    @Override
    public void addAlias(String nodeId, String alias, String tenantId) {
        delegate().addAlias(nodeId, alias, tenantId);
        persistMutation(new GraphMutation.AliasAdded(
            nodeId, alias, Instant.now(), MutationContext.get()), tenantId);
    }

    @Override
    public void removeAlias(String nodeId, String alias, String tenantId) {
        delegate().removeAlias(nodeId, alias, tenantId);
        persistMutation(new GraphMutation.AliasRemoved(
            nodeId, alias, Instant.now(), MutationContext.get()), tenantId);
    }

    @Override
    public String createSubgraph(SubgraphInput input, String tenantId) {
        String subgraphId = delegate().createSubgraph(input, tenantId);
        persistMutation(new GraphMutation.SubgraphCreated(
            subgraphId, input.name(), input.type(),
            Instant.now(), MutationContext.get()), tenantId);
        return subgraphId;
    }

    @Override
    public int eraseSubgraph(String subgraphId, String tenantId) {
        int affected = delegate().eraseSubgraph(subgraphId, tenantId);
        persistMutation(new GraphMutation.SubgraphErased(
            subgraphId, affected, Instant.now(), MutationContext.get()), tenantId);
        return affected;
    }

    @Override
    public int eraseEntity(String entityName, String tenantId) {
        int affected = delegate().eraseEntity(entityName, tenantId);
        persistMutation(new GraphMutation.EntityErased(
            entityName, affected, Instant.now(), MutationContext.get()), tenantId);
        return affected;
    }

    @Override
    public int eraseEntityAcrossTenants(String entityName, Set<String> tenantIds) {
        int count = 0;
        for (String tid : tenantIds) {
            count += this.eraseEntity(entityName, tid);
        }
        return count;
    }

    private void persistMutation(GraphMutation mutation, String tenantId) {
        if (snapshotStore != null) {
            snapshotStore.storeMutation(tenantId, mutation);
        }
        if (eventSink != null) {
            eventSink.accept(new GraphMutationRecorded(tenantId, mutation));
        }
    }
}

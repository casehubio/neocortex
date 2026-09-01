package io.casehub.neocortex.mindmap;

import java.util.List;
import java.util.Map;
import java.util.Set;

class StubMindMapStore implements MindMapStore {

    private final Map<String, List<MindMapEdge>> neighborsByNode;

    StubMindMapStore(Map<String, List<MindMapEdge>> neighborsByNode) {
        this.neighborsByNode = Map.copyOf(neighborsByNode);
    }

    @Override
    public List<MindMapEdge> neighbors(String nodeId, String tenantId) {
        return neighborsByNode.getOrDefault(nodeId, List.of());
    }

    @Override
    public Set<MindMapCapability> capabilities() { return Set.of(); }

    public void registerVocabulary(MindMapVocabulary v) { throw new UnsupportedOperationException(); }
    public String addNode(NodeInput input, String tenantId) { throw new UnsupportedOperationException(); }
    public MindMapNode getNode(String nodeId, String tenantId) { throw new UnsupportedOperationException(); }
    public void updateNode(String nodeId, NodeUpdate update, String tenantId) { throw new UnsupportedOperationException(); }
    public String addEdge(EdgeInput input, String tenantId) { throw new UnsupportedOperationException(); }
    public MindMapEdge getEdge(String edgeId, String tenantId) { throw new UnsupportedOperationException(); }
    public void removeEdge(String edgeId, String tenantId) { throw new UnsupportedOperationException(); }
    public void addAlias(String nodeId, String alias, String tenantId) { throw new UnsupportedOperationException(); }
    public void removeAlias(String nodeId, String alias, String tenantId) { throw new UnsupportedOperationException(); }
    public MindMapNode resolveNode(String nameOrAlias, String subgraphId, String tenantId) { throw new UnsupportedOperationException(); }
    public MergeResult mergeNodes(String keepNodeId, String removeNodeId, String tenantId) { throw new UnsupportedOperationException(); }
    public String createSubgraph(SubgraphInput input, String tenantId) { throw new UnsupportedOperationException(); }
    public MindMapSubgraph getSubgraph(String subgraphId, String tenantId) { throw new UnsupportedOperationException(); }
    public void updateSubgraph(String subgraphId, String rootNodeId, String tenantId) { throw new UnsupportedOperationException(); }
    public List<MindMapSubgraph> listSubgraphs(String tenantId) { throw new UnsupportedOperationException(); }
    public List<MindMapNode> nodesIn(String subgraphId, String tenantId) { throw new UnsupportedOperationException(); }
    public List<MindMapEdge> bridgeEdges(String subgraphId, String tenantId) { throw new UnsupportedOperationException(); }
    public List<MindMapEdge> neighbors(String nodeId, String edgeType, String tenantId) { throw new UnsupportedOperationException(); }
    public List<MindMapNode> search(MindMapQuery query) { throw new UnsupportedOperationException(); }
    public void supersede(String targetId, String supersedingId, String reason, String tenantId) { throw new UnsupportedOperationException(); }
    public void reinstate(String targetId, String tenantId) { throw new UnsupportedOperationException(); }
    public SupersessionStatus getSupersessionStatus(String targetId, String tenantId) { throw new UnsupportedOperationException(); }
    public int eraseNode(String nodeId, String tenantId) { throw new UnsupportedOperationException(); }
    public int eraseSubgraph(String subgraphId, String tenantId) { throw new UnsupportedOperationException(); }
    public int eraseEntity(String entityName, String tenantId) { throw new UnsupportedOperationException(); }
    public int eraseEntityAcrossTenants(String entityName, Set<String> tenantIds) { throw new UnsupportedOperationException(); }
}

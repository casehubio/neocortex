package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DerivedEdgeDecorator implements MindMapStore {

    private static final int DEFAULT_MAX_DEPTH = 3;
    private static final ThreadLocal<Integer> derivationDepth = ThreadLocal.withInitial(() -> 0);

    private final MindMapStore delegate;
    private final List<DerivedEdgeRule> rules;
    private final int maxDepth;
    private final Map<String, List<String>> triggerToDerived = new ConcurrentHashMap<>();

    public DerivedEdgeDecorator(MindMapStore delegate, List<DerivedEdgeRule> rules) {
        this(delegate, rules, DEFAULT_MAX_DEPTH);
    }

    public DerivedEdgeDecorator(MindMapStore delegate, List<DerivedEdgeRule> rules, int maxDepth) {
        this.delegate = delegate;
        this.rules = List.copyOf(rules);
        this.maxDepth = maxDepth;
    }

    @Override
    public String addEdge(EdgeInput input, String tenantId) {
        String edgeId = delegate.addEdge(input, tenantId);

        int depth = derivationDepth.get();
        if (depth >= maxDepth) {
            return edgeId;
        }

        derivationDepth.set(depth + 1);
        try {
            MindMapEdge trigger = delegate.getEdge(edgeId, tenantId);
            MindMapNode sourceNode = delegate.getNode(input.sourceNodeId(), tenantId);
            if (trigger == null || sourceNode == null) {
                return edgeId;
            }

            for (DerivedEdgeRule rule : rules) {
                List<EdgeInput> derived = rule.derive(sourceNode, trigger, delegate);
                if (derived == null) continue;
                for (EdgeInput d : derived) {
                    EdgeInput withProvenance = addProvenance(d, edgeId, rule.name());
                    String derivedId = this.addEdge(withProvenance, tenantId);
                    triggerToDerived.computeIfAbsent(edgeId, k -> new ArrayList<>()).add(derivedId);
                }
            }
        } finally {
            derivationDepth.set(depth);
        }

        return edgeId;
    }

    @Override
    public void removeEdge(String edgeId, String tenantId) {
        List<String> derivedIds = triggerToDerived.remove(edgeId);
        if (derivedIds != null) {
            for (String derivedId : derivedIds) {
                this.removeEdge(derivedId, tenantId);
            }
        }
        delegate.removeEdge(edgeId, tenantId);
    }

    private static EdgeInput addProvenance(EdgeInput input, String triggerEdgeId, String ruleName) {
        Map<String, String> props = new HashMap<>(input.properties());
        props.put(DerivedEdgeRule.PROPERTY_DERIVED, "true");
        props.put(DerivedEdgeRule.PROPERTY_TRIGGER_EDGE_ID, triggerEdgeId);
        props.put(DerivedEdgeRule.PROPERTY_RULE_NAME, ruleName);
        return new EdgeInput(
            input.sourceNodeId(), input.targetNodeId(), input.edgeType(),
            input.confidenceOrigin(), input.confidence(), input.provenance(),
            input.validFrom(), input.validUntil(),
            input.pleasure(), input.arousal(), input.dominance(),
            props);
    }

    // --- Delegate all other methods ---

    @Override public void registerVocabulary(MindMapVocabulary vocabulary) { delegate.registerVocabulary(vocabulary); }
    @Override public String addNode(NodeInput input, String tenantId) { return delegate.addNode(input, tenantId); }
    @Override public MindMapNode getNode(String nodeId, String tenantId) { return delegate.getNode(nodeId, tenantId); }
    @Override public void updateNode(String nodeId, NodeUpdate update, String tenantId) { delegate.updateNode(nodeId, update, tenantId); }
    @Override public MindMapEdge getEdge(String edgeId, String tenantId) { return delegate.getEdge(edgeId, tenantId); }
    @Override public void addAlias(String nodeId, String alias, String tenantId) { delegate.addAlias(nodeId, alias, tenantId); }
    @Override public void removeAlias(String nodeId, String alias, String tenantId) { delegate.removeAlias(nodeId, alias, tenantId); }
    @Override public MindMapNode resolveNode(String nameOrAlias, String subgraphId, String tenantId) { return delegate.resolveNode(nameOrAlias, subgraphId, tenantId); }
    @Override public MergeResult mergeNodes(String keepNodeId, String removeNodeId, String tenantId) { return delegate.mergeNodes(keepNodeId, removeNodeId, tenantId); }
    @Override public String createSubgraph(SubgraphInput input, String tenantId) { return delegate.createSubgraph(input, tenantId); }
    @Override public MindMapSubgraph getSubgraph(String subgraphId, String tenantId) { return delegate.getSubgraph(subgraphId, tenantId); }
    @Override public void updateSubgraph(String subgraphId, String rootNodeId, String tenantId) { delegate.updateSubgraph(subgraphId, rootNodeId, tenantId); }
    @Override public List<MindMapNode> nodesIn(String subgraphId, String tenantId) { return delegate.nodesIn(subgraphId, tenantId); }
    @Override public List<MindMapEdge> bridgeEdges(String subgraphId, String tenantId) { return delegate.bridgeEdges(subgraphId, tenantId); }
    @Override public List<MindMapEdge> neighbors(String nodeId, String tenantId) { return delegate.neighbors(nodeId, tenantId); }
    @Override public List<MindMapEdge> neighbors(String nodeId, String edgeType, String tenantId) { return delegate.neighbors(nodeId, edgeType, tenantId); }
    @Override public List<MindMapNode> search(MindMapQuery query) { return delegate.search(query); }
    @Override public void supersede(String targetId, String supersedingId, String reason, String tenantId) { delegate.supersede(targetId, supersedingId, reason, tenantId); }
    @Override public void reinstate(String targetId, String tenantId) { delegate.reinstate(targetId, tenantId); }
    @Override public SupersessionStatus getSupersessionStatus(String targetId, String tenantId) { return delegate.getSupersessionStatus(targetId, tenantId); }
    @Override public int eraseNode(String nodeId, String tenantId) { return delegate.eraseNode(nodeId, tenantId); }
    @Override public int eraseSubgraph(String subgraphId, String tenantId) { return delegate.eraseSubgraph(subgraphId, tenantId); }
    @Override public int eraseEntity(String entityName, String tenantId) { return delegate.eraseEntity(entityName, tenantId); }
    @Override public int eraseEntityAcrossTenants(String entityName, Set<String> tenantIds) { return delegate.eraseEntityAcrossTenants(entityName, tenantIds); }
    @Override public Set<MindMapCapability> capabilities() { return delegate.capabilities(); }
}

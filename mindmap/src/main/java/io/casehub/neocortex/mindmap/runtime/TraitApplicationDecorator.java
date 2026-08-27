package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MergeResult;
import io.casehub.neocortex.mindmap.MindMapCapability;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.MindMapVocabulary;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SupersessionStatus;
import io.casehub.neocortex.mindmap.TraitRule;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Decorator
@Priority(70)
public class TraitApplicationDecorator implements MindMapStore {

    private static final ThreadLocal<Boolean> evaluating =
        ThreadLocal.withInitial(() -> false);

    private final MindMapStore      delegate;
    private final List<TraitRule>    rules;

    @Inject
    public TraitApplicationDecorator(@Delegate @Any MindMapStore delegate,
                                     Instance<TraitRule> rules) {
        this(delegate, rules.stream().toList());
    }

    TraitApplicationDecorator(MindMapStore delegate, List<TraitRule> rules) {
        this.delegate = delegate;
        this.rules    = List.copyOf(rules);
    }

    @Override
    public String addNode(NodeInput input, String tenantId) {
        String nodeId = delegate.addNode(input, tenantId);
        if (!evaluating.get()) {
            evaluating.set(true);
            try {
                evaluateTraitsForNode(nodeId, tenantId);
            } finally {
                evaluating.set(false);
            }
        }
        return nodeId;
    }

    @Override
    public void updateNode(String nodeId, NodeUpdate update, String tenantId) {
        delegate.updateNode(nodeId, update, tenantId);
        if (!evaluating.get()) {
            evaluating.set(true);
            try {
                evaluateTraitsForNode(nodeId, tenantId);
            } finally {
                evaluating.set(false);
            }
        }
    }

    @Override
    public String addEdge(EdgeInput input, String tenantId) {
        String edgeId = delegate.addEdge(input, tenantId);
        if (!evaluating.get()) {
            evaluating.set(true);
            try {
                evaluateTraitsForNode(input.sourceNodeId(), tenantId);
                evaluateTraitsForNode(input.targetNodeId(), tenantId);
            } finally {
                evaluating.set(false);
            }
        }
        return edgeId;
    }

    @Override
    public void removeEdge(String edgeId, String tenantId) {
        MindMapEdge edge = delegate.getEdge(edgeId, tenantId);
        String sourceId = edge != null ? edge.sourceNodeId() : null;
        String targetId = edge != null ? edge.targetNodeId() : null;

        delegate.removeEdge(edgeId, tenantId);

        if (!evaluating.get() && edge != null) {
            evaluating.set(true);
            try {
                evaluateTraitsForNode(sourceId, tenantId);
                evaluateTraitsForNode(targetId, tenantId);
            } finally {
                evaluating.set(false);
            }
        }
    }

    private void evaluateTraitsForNode(String nodeId, String tenantId) {
        if (rules.isEmpty()) return;

        MindMapNode node = delegate.getNode(nodeId, tenantId);
        if (node == null) return;

        List<MindMapEdge> edges = delegate.neighbors(nodeId, tenantId);

        Map<String, Boolean> traitMatches = new HashMap<>();
        for (TraitRule rule : rules) {
            boolean matches = rule.matches(node, edges);
            traitMatches.merge(rule.traitName(), matches, (a, b) -> a || b);
        }

        Set<String> traitsToAdd    = new LinkedHashSet<>();
        Set<String> traitsToRemove = new LinkedHashSet<>();

        for (var entry : traitMatches.entrySet()) {
            String traitName = entry.getKey();
            boolean anyMatch = entry.getValue();
            boolean present  = node.traits().contains(traitName);

            if (anyMatch && !present) {
                traitsToAdd.add(traitName);
            } else if (!anyMatch && present) {
                traitsToRemove.add(traitName);
            }
        }

        if (!traitsToAdd.isEmpty() || !traitsToRemove.isEmpty()) {
            delegate.updateNode(nodeId,
                new NodeUpdate(null, null, null, null,
                    traitsToAdd.isEmpty() ? null : traitsToAdd,
                    traitsToRemove.isEmpty() ? null : traitsToRemove,
                    null, null, null, null, null, null, null, null, null),
                tenantId);
        }
    }


    // --- Delegate all other methods ---

    @Override
    public void registerVocabulary(MindMapVocabulary vocabulary)                                 {delegate.registerVocabulary(vocabulary);}

    @Override
    public MindMapNode getNode(String nodeId, String tenantId)                                   {return delegate.getNode(nodeId, tenantId);}

    @Override
    public MindMapEdge getEdge(String edgeId, String tenantId)                                   {return delegate.getEdge(edgeId, tenantId);}

    @Override
    public void addAlias(String nodeId, String alias, String tenantId)                           {delegate.addAlias(nodeId, alias, tenantId);}

    @Override
    public void removeAlias(String nodeId, String alias, String tenantId)                        {delegate.removeAlias(nodeId, alias, tenantId);}

    @Override
    public MindMapNode resolveNode(String nameOrAlias, String subgraphId, String tenantId)       {return delegate.resolveNode(nameOrAlias, subgraphId, tenantId);}

    @Override
    public MergeResult mergeNodes(String keepNodeId, String removeNodeId, String tenantId)       {return delegate.mergeNodes(keepNodeId, removeNodeId, tenantId);}

    @Override
    public String createSubgraph(SubgraphInput input, String tenantId)                           {return delegate.createSubgraph(input, tenantId);}

    @Override
    public MindMapSubgraph getSubgraph(String subgraphId, String tenantId)                       {return delegate.getSubgraph(subgraphId, tenantId);}

    @Override
    public void updateSubgraph(String subgraphId, String rootNodeId, String tenantId)            {delegate.updateSubgraph(subgraphId, rootNodeId, tenantId);}

    @Override
    public List<MindMapSubgraph> listSubgraphs(String tenantId) {
        return delegate.listSubgraphs(tenantId);
    }


    @Override
    public List<MindMapNode> nodesIn(String subgraphId, String tenantId)                         {return delegate.nodesIn(subgraphId, tenantId);}

    @Override
    public List<MindMapEdge> bridgeEdges(String subgraphId, String tenantId)                     {return delegate.bridgeEdges(subgraphId, tenantId);}

    @Override
    public List<MindMapEdge> neighbors(String nodeId, String tenantId)                           {return delegate.neighbors(nodeId, tenantId);}

    @Override
    public List<MindMapEdge> neighbors(String nodeId, String edgeType, String tenantId)          {return delegate.neighbors(nodeId, edgeType, tenantId);}

    @Override
    public List<MindMapNode> search(MindMapQuery query)                                          {return delegate.search(query);}

    @Override
    public void supersede(String targetId, String supersedingId, String reason, String tenantId) {delegate.supersede(targetId, supersedingId, reason, tenantId);}

    @Override
    public void reinstate(String targetId, String tenantId)                                      {delegate.reinstate(targetId, tenantId);}

    @Override
    public SupersessionStatus getSupersessionStatus(String targetId, String tenantId)            {return delegate.getSupersessionStatus(targetId, tenantId);}

    @Override
    public int eraseNode(String nodeId, String tenantId)                                         {return delegate.eraseNode(nodeId, tenantId);}

    @Override
    public int eraseSubgraph(String subgraphId, String tenantId)                                 {return delegate.eraseSubgraph(subgraphId, tenantId);}

    @Override
    public int eraseEntity(String entityName, String tenantId)                                   {return delegate.eraseEntity(entityName, tenantId);}

    @Override
    public int eraseEntityAcrossTenants(String entityName, Set<String> tenantIds)                {return delegate.eraseEntityAcrossTenants(entityName, tenantIds);}

    @Override
    public Set<MindMapCapability> capabilities()                                                 {return delegate.capabilities();}
}

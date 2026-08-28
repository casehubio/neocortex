package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.AbstractForwardingMindMapStore;
import io.casehub.neocortex.mindmap.DerivedEdgeRule;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DerivedEdgeDecorator extends AbstractForwardingMindMapStore {

    private static final int                  DEFAULT_MAX_DEPTH = 3;
    private static final ThreadLocal<Integer> derivationDepth   = ThreadLocal.withInitial(() -> 0);

    private final List<DerivedEdgeRule>     rules;
    private final int                       maxDepth;
    private final Map<String, List<String>> triggerToDerived = new ConcurrentHashMap<>();

    @Inject
    public DerivedEdgeDecorator(@Delegate @Any MindMapStore delegate,
                                Instance<DerivedEdgeRule> rules) {
        this(delegate, rules.stream().toList(), DEFAULT_MAX_DEPTH);
    }

    DerivedEdgeDecorator(MindMapStore delegate, List<DerivedEdgeRule> rules) {
        this(delegate, rules, DEFAULT_MAX_DEPTH);
    }

    DerivedEdgeDecorator(MindMapStore delegate, List<DerivedEdgeRule> rules, int maxDepth) {
        super(delegate);
        this.rules    = List.copyOf(rules);
        this.maxDepth = maxDepth;
    }

    @Override
    public String addEdge(EdgeInput input, String tenantId) {
        String edgeId = delegate().addEdge(input, tenantId);

        int depth = derivationDepth.get();
        if (depth >= maxDepth) {
            return edgeId;
        }

        derivationDepth.set(depth + 1);
        try {
            MindMapEdge trigger    = delegate().getEdge(edgeId, tenantId);
            MindMapNode sourceNode = delegate().getNode(input.sourceNodeId(), tenantId);
            if (trigger == null || sourceNode == null) {
                return edgeId;
            }

            for (DerivedEdgeRule rule : rules) {
                List<EdgeInput> derived = rule.derive(sourceNode, trigger, delegate());
                if (derived == null) {continue;}
                for (EdgeInput d : derived) {
                    EdgeInput withProvenance = addProvenance(d, edgeId, rule.name());
                    String    derivedId      = this.addEdge(withProvenance, tenantId);
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
        delegate().removeEdge(edgeId, tenantId);
    }

    @Override
    public int eraseNode(String nodeId, String tenantId) {
        cleanMapForEdges(delegate().neighbors(nodeId, tenantId));
        return delegate().eraseNode(nodeId, tenantId);
    }

    @Override
    public int eraseSubgraph(String subgraphId, String tenantId) {
        for (MindMapNode node : delegate().nodesIn(subgraphId, tenantId)) {
            cleanMapForEdges(delegate().neighbors(node.id(), tenantId));
        }
        return delegate().eraseSubgraph(subgraphId, tenantId);
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

    private void cleanMapForEdges(List<MindMapEdge> edges) {
        for (MindMapEdge edge : edges) {
            triggerToDerived.remove(edge.id());
        }
    }
}

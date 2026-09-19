package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.cognitive.index.DeclarativeRuleRegistry;
import io.casehub.neocortex.mindmap.AbstractForwardingMindMapStore;
import io.casehub.neocortex.mindmap.DerivedEdgeRule;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.platform.api.identity.PrincipalId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DerivedEdgeDecorator extends AbstractForwardingMindMapStore {

    private static final int                  DEFAULT_MAX_DEPTH = 3;
    private static final ThreadLocal<Integer> derivationDepth   = ThreadLocal.withInitial(() -> 0);

    private final List<DerivedEdgeRule>     programmaticRules;
    private final DeclarativeRuleRegistry   registry;
    private final int                       maxDepth;
    private final Map<String, List<String>> triggerToDerived = new ConcurrentHashMap<>();

    public DerivedEdgeDecorator(MindMapStore delegate, List<DerivedEdgeRule> rules) {
        this(delegate, rules, DEFAULT_MAX_DEPTH, null);
    }

    public DerivedEdgeDecorator(MindMapStore delegate, List<DerivedEdgeRule> rules, int maxDepth) {
        this(delegate, rules, maxDepth, null);
    }

    public DerivedEdgeDecorator(MindMapStore delegate, List<DerivedEdgeRule> rules, int maxDepth,
                                DeclarativeRuleRegistry registry) {
        super(delegate);
        this.programmaticRules = List.copyOf(rules);
        this.maxDepth          = maxDepth;
        this.registry          = registry;
    }

    private List<DerivedEdgeRule> resolveRules(PrincipalId principal) {
        if (registry == null) {return programmaticRules;}
        List<DerivedEdgeRule> resolved = new ArrayList<>(programmaticRules);
        if (principal != null) {
            resolved.addAll(registry.derivedEdgeRules(principal.id()));
        } else {
            resolved.addAll(registry.allDerivedEdgeRules());
        }
        return resolved;
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

            for (DerivedEdgeRule rule : resolveRules(input.principalId())) {
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
    public List<String> addEdges(List<EdgeInput> inputs, String tenantId) {
        List<String> edgeIds = delegate().addEdges(inputs, tenantId);

        int depth = derivationDepth.get();
        if (depth >= maxDepth) {
            return edgeIds;
        }

        derivationDepth.set(depth + 1);
        try {
            for (int i = 0; i < edgeIds.size(); i++) {
                String      edgeId     = edgeIds.get(i);
                EdgeInput   input      = inputs.get(i);
                MindMapEdge trigger    = delegate().getEdge(edgeId, tenantId);
                MindMapNode sourceNode = delegate().getNode(input.sourceNodeId(), tenantId);
                if (trigger == null || sourceNode == null) {
                    continue;
                }

                for (DerivedEdgeRule rule : resolveRules(input.principalId())) {
                    List<EdgeInput> derived = rule.derive(sourceNode, trigger, delegate());
                    if (derived == null) {continue;}
                    for (EdgeInput d : derived) {
                        EdgeInput withProvenance = addProvenance(d, edgeId, rule.name());
                        String    derivedId      = this.addEdge(withProvenance, tenantId);
                        triggerToDerived.computeIfAbsent(edgeId, k -> new ArrayList<>()).add(derivedId);
                    }
                }
            }
        } finally {
            derivationDepth.set(depth);
        }

        return edgeIds;
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
                input.confidence(), input.provenance(),
                input.validFrom(), input.validUntil(),
                input.pleasure(), input.arousal(), input.dominance(),
                props, input.principalId());
    }

    private void cleanMapForEdges(List<MindMapEdge> edges) {
        for (MindMapEdge edge : edges) {
            triggerToDerived.remove(edge.id());
        }
    }
}

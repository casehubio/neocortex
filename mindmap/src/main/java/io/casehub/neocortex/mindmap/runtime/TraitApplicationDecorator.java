package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.AbstractForwardingMindMapStore;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
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
public class TraitApplicationDecorator extends AbstractForwardingMindMapStore {

    private static final ThreadLocal<Boolean> evaluating =
            ThreadLocal.withInitial(() -> false);

    private final List<TraitRule> rules;

    @Inject
    public TraitApplicationDecorator(@Delegate @Any MindMapStore delegate,
                                     Instance<TraitRule> rules) {
        this(delegate, rules.stream().toList());
    }

    TraitApplicationDecorator(MindMapStore delegate, List<TraitRule> rules) {
        super(delegate);
        this.rules = List.copyOf(rules);
    }

    @Override
    public String addNode(NodeInput input, String tenantId) {
        String nodeId = delegate().addNode(input, tenantId);
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
        delegate().updateNode(nodeId, update, tenantId);
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
        String edgeId = delegate().addEdge(input, tenantId);
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
        MindMapEdge edge     = delegate().getEdge(edgeId, tenantId);
        String      sourceId = edge != null ? edge.sourceNodeId() : null;
        String      targetId = edge != null ? edge.targetNodeId() : null;

        delegate().removeEdge(edgeId, tenantId);

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
        if (rules.isEmpty()) {return;}

        MindMapNode node = delegate().getNode(nodeId, tenantId);
        if (node == null) {return;}

        List<MindMapEdge> edges = delegate().neighbors(nodeId, tenantId);

        Map<String, Boolean> traitMatches = new HashMap<>();
        for (TraitRule rule : rules) {
            boolean matches = rule.matches(node, edges);
            traitMatches.merge(rule.traitName(), matches, (a, b) -> a || b);
        }

        Set<String> traitsToAdd    = new LinkedHashSet<>();
        Set<String> traitsToRemove = new LinkedHashSet<>();

        for (var entry : traitMatches.entrySet()) {
            String  traitName = entry.getKey();
            boolean anyMatch  = entry.getValue();
            boolean present   = node.traits().contains(traitName);

            if (anyMatch && !present) {
                traitsToAdd.add(traitName);
            } else if (!anyMatch && present) {
                traitsToRemove.add(traitName);
            }
        }

        if (!traitsToAdd.isEmpty() || !traitsToRemove.isEmpty()) {
            delegate().updateNode(nodeId,
                                  new NodeUpdate(null, null, null, null,
                                                 traitsToAdd.isEmpty() ? null : traitsToAdd,
                                                 traitsToRemove.isEmpty() ? null : traitsToRemove,
                                                 null, null, null, null, null, null, null, null, null),
                                  tenantId);
        }
    }
}

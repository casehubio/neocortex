/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.neocortex.mindmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record DeclarativeDerivedEdgeRule(
    String name,
    Set<String> triggerEdgeTypes,
    TraversalSpec traverse,
    List<EdgeDerivation> derivations
) implements DerivedEdgeRule {

    public DeclarativeDerivedEdgeRule {
        Objects.requireNonNull(name, "name required");
        triggerEdgeTypes = Set.copyOf(triggerEdgeTypes);
        derivations = List.copyOf(derivations);
    }

    @Override
    public String name() { return name; }

    @Override
    public List<EdgeInput> derive(MindMapNode sourceNode, MindMapEdge trigger,
                                   MindMapStore store) {
        if (!triggerEdgeTypes.contains(trigger.edgeType())) {
            return List.of();
        }
        if (traverse == null) {
            return deriveDirect(trigger);
        }
        return deriveWithTraversal(trigger, store);
    }

    private List<EdgeInput> deriveDirect(MindMapEdge trigger) {
        List<EdgeInput> result = new ArrayList<>();
        for (EdgeDerivation d : derivations) {
            String src = resolveRef(d.source(), trigger, null);
            String tgt = resolveRef(d.target(), trigger, null);
            result.add(new EdgeInput(src, tgt, d.edgeType(),
                d.confidence(), null, null, null, null, null, null, d.properties()));
        }
        return result;
    }

    private List<EdgeInput> deriveWithTraversal(MindMapEdge trigger, MindMapStore store) {
        if (store == null) return List.of();
        String startNodeId = resolveRef(traverse.from(), trigger, null);
        List<EdgeInput> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        walkGraph(store, startNodeId, trigger, traverse.maxDepth(), result, visited);
        return result;
    }

    private void walkGraph(MindMapStore store, String currentNodeId,
            MindMapEdge trigger, int remainingDepth, List<EdgeInput> result,
            Set<String> visited) {
        if (remainingDepth <= 0 || !visited.add(currentNodeId)) return;

        List<MindMapEdge> neighbors = store.neighbors(currentNodeId, null);
        for (MindMapEdge edge : neighbors) {
            if (!traverse.follow().equals(edge.edgeType())) continue;

            String nextNodeId;
            if (traverse.direction() == TraversalSpec.TraversalDirection.OUTBOUND) {
                if (!edge.sourceNodeId().equals(currentNodeId)) continue;
                nextNodeId = edge.targetNodeId();
            } else {
                if (!edge.targetNodeId().equals(currentNodeId)) continue;
                nextNodeId = edge.sourceNodeId();
            }

            for (EdgeDerivation d : derivations) {
                String src = resolveRef(d.source(), trigger, nextNodeId);
                String tgt = resolveRef(d.target(), trigger, nextNodeId);
                result.add(new EdgeInput(src, tgt, d.edgeType(),
                    d.confidence(), null, null, null, null, null, null, d.properties()));
            }

            walkGraph(store, nextNodeId, trigger, remainingDepth - 1, result, visited);
        }
    }

    private static String resolveRef(EdgeRef ref, MindMapEdge trigger, String traversalNode) {
        return switch (ref) {
            case TRIGGER_SOURCE -> trigger.sourceNodeId();
            case TRIGGER_TARGET -> trigger.targetNodeId();
            case TRAVERSAL_NODE -> {
                if (traversalNode == null)
                    throw new IllegalStateException("TRAVERSAL_NODE used without traverse block");
                yield traversalNode;
            }
        };
    }
}

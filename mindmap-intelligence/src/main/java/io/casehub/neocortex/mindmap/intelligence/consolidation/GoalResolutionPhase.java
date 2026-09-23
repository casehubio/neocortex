package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.CognitiveGoalDecomposer;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.GoalDecompositionResult;
import io.casehub.neocortex.mindmap.GoalLifecycleProvider;
import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(35)
public class GoalResolutionPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(GoalResolutionPhase.class.getName());
    private static final Set<String> DISTANT_HORIZONS = Set.of("aspirational", "long");
    private static final Set<String> DAG_EDGE_TYPES = Set.of("blocks", "requires", "decomposes-into");
    private static final Set<String> RESOLVED_STATUSES = Set.of("completed", "abandoned");

    private final MindMapStore store;
    private final CognitiveGoalDecomposer decomposer;
    private final GoalLifecycleProvider lifecycleProvider;

    @Inject
    public GoalResolutionPhase(Instance<MindMapStore> store,
                                Instance<CognitiveGoalDecomposer> decomposer,
                                Instance<GoalLifecycleProvider> lifecycleProvider) {
        this.store = store.isResolvable() ? store.get() : null;
        this.decomposer = decomposer.isResolvable() ? decomposer.get() : null;
        this.lifecycleProvider = lifecycleProvider.isResolvable() ? lifecycleProvider.get() : null;
    }

    public GoalResolutionPhase(MindMapStore store,
                         CognitiveGoalDecomposer decomposer,
                         GoalLifecycleProvider lifecycleProvider) {
        this.store = store;
        this.decomposer = decomposer;
        this.lifecycleProvider = lifecycleProvider;
    }

    @Override
    public String name() {
        return "goal-resolution";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (store == null) {return;}

        String goalSgId = findGoalSubgraph(tenantId);
        if (goalSgId == null) {return;}

        List<MindMapNode> goalNodes = store.nodesIn(goalSgId, tenantId);
        if (goalNodes.isEmpty()) {return;}

        prune(goalNodes, tenantId);
        expand(tenantId, goalSgId);
        merge(tenantId, goalSgId);
        revise(tenantId, goalSgId);
        sync(tenantId, goalSgId);
    }

    private void prune(List<MindMapNode> goalNodes, String tenantId) {
        for (MindMapNode node : goalNodes) {
            String resolution = node.property("resolution").orElse(null);
            if (!"high".equals(resolution) && !"medium".equals(resolution)) continue;

            String horizon = node.property("horizon").orElse(null);
            if (horizon == null || !DISTANT_HORIZONS.contains(horizon)) continue;

            double urgency = node.property("urgency").map(Double::parseDouble).orElse(0.0);
            if (urgency > 0.7) continue;

            List<MindMapEdge> decomposeEdges = store.neighbors(node.id(), "decomposes-into", tenantId)
                    .stream()
                    .filter(e -> e.sourceNodeId().equals(node.id()))
                    .toList();

            if (decomposeEdges.isEmpty()) continue;

            for (MindMapEdge edge : decomposeEdges) {
                try {
                    store.eraseNode(edge.targetNodeId(), tenantId);
                } catch (Exception e) {
                    LOG.fine("Could not remove sub-goal: " + e.getMessage());
                }
            }

            store.updateNode(node.id(),
                    NodeUpdate.empty().withPropertiesToSet(Map.of("resolution", "low")),
                    tenantId);
            LOG.fine("Pruned sub-goals for distant goal: " + node.name());
        }
    }

    private void expand(String tenantId, String goalSgId) {
        if (decomposer == null) {return;}

        List<MindMapNode> goalNodes = store.nodesIn(goalSgId, tenantId);
        for (MindMapNode node : goalNodes) {
            String resolution = node.property("resolution").orElse("low");
            if ("high".equals(resolution)) {continue;}

            double urgency = node.property("urgency").map(Double::parseDouble).orElse(0.0);
            if (urgency <= 0.7) {continue;}

            String description = node.property("description").orElse(node.name());
            List<MindMapNode> contextNodes = goalNodes.stream()
                                                      .filter(n -> !n.id().equals(node.id()))
                                                      .toList();

            GoalDecompositionResult result = decomposer.decompose(description, contextNodes, tenantId);
            if (result.subGoals().isEmpty()) {continue;}

            Map<String, String> nameToId = new HashMap<>();
            nameToId.put(description, node.id());

            for (GoalDecompositionResult.SubGoal subGoal : result.subGoals()) {
                Map<String, String> props = new HashMap<>(subGoal.properties());
                props.put("description", subGoal.description());
                props.put("status", "active");
                if (subGoal.suggestedHorizon() != null) {
                    props.put("horizon", subGoal.suggestedHorizon());
                }

                String subId = store.addNode(NodeInput.of(subGoal.description(), goalSgId)
                                                      .withProperties(props), tenantId);
                nameToId.put(subGoal.description(), subId);
            }

            for (GoalDecompositionResult.GoalRelationship rel : result.relationships()) {
                String sourceId = nameToId.get(rel.sourceDescription());
                String targetId = nameToId.get(rel.targetDescription());
                if (sourceId != null && targetId != null) {
                    store.addEdge(EdgeInput.of(sourceId, targetId, rel.edgeType()), tenantId);
                }
            }

            store.updateNode(node.id(),
                             NodeUpdate.empty().withPropertiesToSet(Map.of("resolution", "high")),
                             tenantId);
            LOG.fine("Expanded goal: " + node.name() + " into " + result.subGoals().size() + " sub-goals");
        }
    }

    private void merge(String tenantId, String goalSgId) {
        List<MindMapNode> goalNodes = store.nodesIn(goalSgId, tenantId);
        if (goalNodes.size() <= 1) {return;}

        Map<String, List<String>> parentsByChild = new HashMap<>();
        for (MindMapNode node : goalNodes) {
            List<MindMapEdge> decomposeEdges = store.neighbors(node.id(), "decomposes-into", tenantId)
                                                    .stream()
                                                    .filter(e -> e.sourceNodeId().equals(node.id()))
                                                    .toList();
            for (MindMapEdge edge : decomposeEdges) {
                parentsByChild.computeIfAbsent(edge.targetNodeId(), k -> new ArrayList<>())
                              .add(node.id());
            }
        }

        Set<String> merged = new HashSet<>();
        List<MindMapNode> childNodes = goalNodes.stream()
                                                .filter(n -> parentsByChild.containsKey(n.id()))
                                                .toList();

        for (int i = 0; i < childNodes.size(); i++) {
            MindMapNode a = childNodes.get(i);
            if (merged.contains(a.id())) {continue;}

            for (int j = i + 1; j < childNodes.size(); j++) {
                MindMapNode b = childNodes.get(j);
                if (merged.contains(b.id())) {continue;}

                double similarity = JaroWinkler.similarity(a.name(), b.name());
                if (similarity >= 0.85) {
                    try {
                        store.mergeNodes(a.id(), b.id(), tenantId);
                        merged.add(b.id());

                        List<String> parentsOfB = parentsByChild.getOrDefault(b.id(), List.of());
                        for (String parentId : parentsOfB) {
                            boolean alreadyLinked = store.neighbors(a.id(), "contributes-to", tenantId)
                                                         .stream()
                                                         .anyMatch(e -> e.targetNodeId().equals(parentId)
                                                                        || e.sourceNodeId().equals(parentId));
                            if (!alreadyLinked) {
                                store.addEdge(EdgeInput.of(a.id(), parentId, "contributes-to"), tenantId);
                            }
                        }
                        LOG.fine("Merged sub-goals: " + a.name() + " and " + b.name());
                    } catch (Exception e) {
                        LOG.fine("Could not merge sub-goals: " + e.getMessage());
                    }
                }
            }
        }
    }


    private void revise(String tenantId, String goalSgId) {
        List<MindMapNode> goalNodes = store.nodesIn(goalSgId, tenantId);

        for (MindMapNode node : goalNodes) {
            if (!"blocked".equals(node.property("status").orElse(null))) continue;

            List<MindMapEdge> blockingEdges = store.neighbors(node.id(), "blocks", tenantId)
                    .stream()
                    .filter(e -> e.targetNodeId().equals(node.id()))
                    .toList();

            boolean allResolved = !blockingEdges.isEmpty() && blockingEdges.stream().allMatch(edge -> {
                MindMapNode blocker = store.getNode(edge.sourceNodeId(), tenantId);
                if (blocker == null) return true;
                String status = blocker.property("status").orElse("active");
                return RESOLVED_STATUSES.contains(status);
            });

            if (allResolved) {
                store.updateNode(node.id(),
                        NodeUpdate.empty().withPropertiesToSet(Map.of("status", "active")),
                        tenantId);
                LOG.fine("Unblocked goal: " + node.name());
            }
        }

        detectAndBreakCycles(tenantId, goalSgId);
    }

    private void detectAndBreakCycles(String tenantId, String goalSgId) {
        List<MindMapNode> goalNodes = store.nodesIn(goalSgId, tenantId);
        Map<String, List<MindMapEdge>> outgoingBySource = new HashMap<>();

        for (MindMapNode node : goalNodes) {
            for (String edgeType : DAG_EDGE_TYPES) {
                List<MindMapEdge> edges = store.neighbors(node.id(), edgeType, tenantId)
                        .stream()
                        .filter(e -> e.sourceNodeId().equals(node.id()))
                        .toList();
                outgoingBySource.computeIfAbsent(node.id(), k -> new ArrayList<>()).addAll(edges);
            }
        }

        for (MindMapNode node : goalNodes) {
            Set<String> visited = new HashSet<>();
            ArrayDeque<String> stack = new ArrayDeque<>();
            stack.push(node.id());
            visited.add(node.id());

            while (!stack.isEmpty()) {
                String current = stack.pop();
                List<MindMapEdge> outgoing = outgoingBySource.getOrDefault(current, List.of());
                for (MindMapEdge edge : outgoing) {
                    String target = edge.targetNodeId();
                    if (target.equals(node.id()) && !current.equals(node.id())) {
                        store.removeEdge(edge.id(), tenantId);
                        LOG.warning("Removed cyclic edge: " + edge.edgeType()
                                + " from " + current + " to " + target);
                        return;
                    }
                    if (visited.add(target)) {
                        stack.push(target);
                    }
                }
            }
        }
    }

    private void sync(String tenantId, String goalSgId) {
        if (lifecycleProvider == null) return;

        List<MindMapNode> goalNodes = store.nodesIn(goalSgId, tenantId);

        Map<String, List<MindMapNode>> byAgent = new HashMap<>();
        for (MindMapNode node : goalNodes) {
            String eidosName = node.property("eidos-goal-name").orElse(null);
            if (eidosName == null) continue;

            String agentId = node.principalId() != null ? node.principalId().id() : "default";
            byAgent.computeIfAbsent(agentId, k -> new ArrayList<>()).add(node);
        }

        for (var entry : byAgent.entrySet()) {
            Map<String, String> states = lifecycleProvider.getLifecycleStates(entry.getKey(), tenantId);
            if (states.isEmpty()) continue;

            for (MindMapNode node : entry.getValue()) {
                String eidosName = node.property("eidos-goal-name").orElse(null);
                String newStatus = states.get(eidosName);
                if (newStatus == null) continue;

                Map<String, String> propsToSet = new HashMap<>();
                propsToSet.put("status", newStatus);

                Set<String> propsToRemove = new HashSet<>();
                if (node.property("decay-signal").isPresent()) {
                    propsToRemove.add("decay-signal");
                }

                store.updateNode(node.id(),
                        NodeUpdate.empty()
                                .withPropertiesToSet(propsToSet)
                                .withPropertiesToRemove(propsToRemove),
                        tenantId);
                LOG.fine("Synced goal '" + node.name() + "' status to: " + newStatus);
            }
        }
    }

    private String findGoalSubgraph(String tenantId) {
        for (MindMapSubgraph sg : store.listSubgraphs(tenantId)) {
            if (SubgraphTypes.GOAL.equals(sg.type())) {
                return sg.id();
            }
        }
        return null;
    }
}

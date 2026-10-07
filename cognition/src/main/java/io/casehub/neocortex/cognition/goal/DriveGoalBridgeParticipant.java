package io.casehub.neocortex.cognition.goal;

import io.casehub.eidos.api.GoalPriority;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.need.NeedTier;
import io.casehub.neocortex.knowledge.TermNormalizer;
import io.casehub.neocortex.mindmap.GoalTier;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.intelligence.consolidation.JaroWinkler;
import io.casehub.platform.api.identity.PrincipalId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DriveGoalBridgeParticipant implements CognitionTickParticipant {

    private static final double JARO_WINKLER_THRESHOLD = 0.85;

    private final GoalProposalOrchestrator orchestrator;
    private final MindMapStore store;
    private final TermNormalizer normalizer;
    private final CognitionConfig config;
    private final ConcurrentHashMap<String, BridgeState> states = new ConcurrentHashMap<>();

    public DriveGoalBridgeParticipant(GoalProposalOrchestrator orchestrator,
                                      MindMapStore store,
                                      TermNormalizer normalizer,
                                      CognitionConfig config) {
        this.orchestrator = Objects.requireNonNull(orchestrator);
        this.store = Objects.requireNonNull(store);
        this.normalizer = Objects.requireNonNull(normalizer);
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public void tick(CognitionTickContext context) {
        if (!config.goalsEnabled()) return;

        String agentId = context.agentId();
        String tenantId = context.tenantId();
        String key = agentId + "|" + tenantId;

        List<DriveGoalProposal> registered = orchestrator.registeredGoals(agentId, tenantId);
        BridgeState state = states.computeIfAbsent(key, k -> new BridgeState());

        String subgraphId = ensureGoalSubgraph(tenantId);
        if (subgraphId == null) return;

        Set<String> currentNames = new HashSet<>();
        for (DriveGoalProposal proposal : registered) {
            currentNames.add(proposal.goalName());
            if (state.persistedNames.contains(proposal.goalName())) continue;
            bridgeProposal(proposal, subgraphId, tenantId, agentId, state);
        }

        syncAbandonments(state, currentNames, tenantId);
    }

    private String ensureGoalSubgraph(String tenantId) {
        for (MindMapSubgraph sg : store.listSubgraphs(tenantId)) {
            if (SubgraphTypes.GOAL.equals(sg.type())) return sg.id();
        }
        return store.createSubgraph(new SubgraphInput(SubgraphTypes.GOAL, SubgraphTypes.GOAL, null), tenantId);
    }

    private void bridgeProposal(DriveGoalProposal proposal, String subgraphId,
                                 String tenantId, String agentId, BridgeState state) {
        String canonical = normalizer.normalize(proposal.goalName(), "general").canonical();
        List<MindMapNode> existingGoals = store.nodesIn(subgraphId, tenantId);

        MindMapNode match = findMatch(canonical, existingGoals);
        if (match != null) {
            enrichExistingNode(match, proposal, tenantId);
            state.persistedNames.add(proposal.goalName());
            state.nameToNodeId.put(proposal.goalName(), match.id());
        } else {
            String nodeId = createGoalNode(proposal, subgraphId, tenantId, agentId);
            state.persistedNames.add(proposal.goalName());
            state.nameToNodeId.put(proposal.goalName(), nodeId);
        }
    }

    private MindMapNode findMatch(String canonical, List<MindMapNode> existing) {
        for (MindMapNode node : existing) {
            if (canonical.equalsIgnoreCase(node.name())) return node;
            String desc = node.properties().get("description");
            if (desc != null && canonical.equalsIgnoreCase(desc)) return node;
        }
        for (MindMapNode node : existing) {
            if (JaroWinkler.similarity(canonical, node.name()) >= JARO_WINKLER_THRESHOLD) return node;
            String desc = node.properties().get("description");
            if (desc != null && JaroWinkler.similarity(canonical, desc) >= JARO_WINKLER_THRESHOLD) return node;
        }
        return null;
    }

    private String createGoalNode(DriveGoalProposal proposal, String subgraphId,
                                  String tenantId, String agentId) {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("description", proposal.goalDescription());
        props.put("status", "active");
        props.put("origin", "drive-proposal");
        props.put("origin-drive", proposal.axis().name());
        props.put("formation-reason", proposal.formationReason());
        String horizon = mapHorizon(proposal.suggestedPriority());
        props.put("horizon", horizon);
        props.put("goal-tier", GoalTier.fromHorizon(horizon).name());
        props.put("drive-intensity", String.valueOf(proposal.driveIntensity()));
        props.put("agent-id", agentId);
        props.put("need-tier", NeedTier.fromDriveAxis(proposal.axis()).name());
        props.put("initial-emotion", "HOPE");
        props.put("initial-emotion-intensity", String.format("%.2f", proposal.driveIntensity()));

        NodeInput input = NodeInput.of(proposal.goalName(), subgraphId)
                                   .withProperties(props)
                                   .withPrincipalId(PrincipalId.agent(agentId));
        return store.addNode(input, tenantId);
    }

    private void enrichExistingNode(MindMapNode node, DriveGoalProposal proposal, String tenantId) {
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("origin-drive", proposal.axis().name());
        updates.put("formation-reason", proposal.formationReason());
        updates.put("need-tier", NeedTier.fromDriveAxis(proposal.axis()).name());
        store.updateNode(node.id(), NodeUpdate.empty().withPropertiesToSet(updates), tenantId);
    }

    private void syncAbandonments(BridgeState state, Set<String> currentNames, String tenantId) {
        var abandoned = new ArrayList<String>();
        for (String name : state.persistedNames) {
            if (!currentNames.contains(name)) {
                String nodeId = state.nameToNodeId.get(name);
                if (nodeId != null) {
                    Map<String, String> updates = new LinkedHashMap<>();
                    updates.put("status", "dormant");
                    updates.put("abandonment-reason", "drive-intensity-below-threshold");
                    store.updateNode(nodeId, NodeUpdate.empty().withPropertiesToSet(updates), tenantId);
                }
                abandoned.add(name);
            }
        }
        for (String name : abandoned) {
            state.persistedNames.remove(name);
            state.nameToNodeId.remove(name);
        }
    }

    static String mapHorizon(GoalPriority priority) {
        if (priority == null) return "long";
        return switch (priority) {
            case PRIMARY -> "medium";
            case SECONDARY -> "long";
        };
    }

    private static final class BridgeState {
        final Set<String> persistedNames = new HashSet<>();
        final Map<String, String> nameToNodeId = new HashMap<>();
    }
}

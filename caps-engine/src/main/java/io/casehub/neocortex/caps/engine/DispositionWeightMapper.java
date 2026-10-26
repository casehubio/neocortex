package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.AgentCapsState;
import io.casehub.neocortex.caps.CapsConnection;
import io.casehub.neocortex.caps.CapsNode;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.ConnectionWeight;
import io.casehub.neocortex.caps.DispositionModifier;
import io.casehub.neocortex.caps.NodeState;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.cognitive.index.DispositionAxes;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

// Personality → CAPS weight initialization — Read & Miller (2002) stable social information processing
public class DispositionWeightMapper {

    private static final Logger LOG = Logger.getLogger(DispositionWeightMapper.class.getName());

    private final CapsTopology topology;
    private final Map<String, Set<String>> tagIndex;

    public DispositionWeightMapper(CapsTopology topology) {
        this.topology = topology;
        this.tagIndex = buildTagIndex(topology);
    }

    public AgentCapsState initializeWeights(String tenantId, String agentId,
                                             DispositionAxes disposition) {
        Map<String, ConnectionWeight> weights = new HashMap<>();
        Map<String, NodeState> nodeStates = new HashMap<>();

        for (CapsConnection conn : topology.connections()) {
            weights.put(conn.id(), new ConnectionWeight(
                conn.defaultWeight(), 0.0, 1.0,
                CapsWeightUpdateConfig.defaults().provenancePrecision(conn.provenance())));
        }

        Map<String, String> axes = Map.of(
            "socialOrient", disposition.socialOrient(),
            "ruleFollowing", disposition.ruleFollowing(),
            "riskAppetite", disposition.riskAppetite(),
            "autonomy", disposition.autonomy(),
            "conflictMode", disposition.conflictMode());

        for (var axisEntry : axes.entrySet()) {
            DispositionModifier mod = topology.dispositionModifiers()
                .get(axisEntry.getKey());
            if (mod == null) continue;

            Map<String, Double> modifiers = mod.valueModifiers()
                .getOrDefault(axisEntry.getValue(), Map.of());

            for (var modEntry : modifiers.entrySet()) {
                applyModifier(modEntry.getKey(), modEntry.getValue(),
                    weights, nodeStates);
            }
        }

        setRestingActivations(disposition, nodeStates);

        return new AgentCapsState(agentId, tenantId, 0,
            Map.copyOf(weights), Map.copyOf(nodeStates));
    }

    private void applyModifier(String name, double multiplier,
                                Map<String, ConnectionWeight> weights,
                                Map<String, NodeState> nodeStates) {
        if (name.contains("_to_")) {
            String[] parts = name.split("_to_", 2);
            String connId = parts[0] + "__" + parts[1];
            if (weights.containsKey(connId)) {
                multiplyWeight(connId, multiplier, weights);
                return;
            }
        }

        if (name.endsWith("_connections") || name.endsWith("_pathways")) {
            String tag = name.replaceAll("_(connections|pathways)$", "")
                .toLowerCase();
            Set<String> connIds = tagIndex.getOrDefault(tag, Set.of());
            if (!connIds.isEmpty()) {
                for (String connId : connIds) {
                    multiplyWeight(connId, multiplier, weights);
                }
                return;
            }
        }

        if (name.endsWith("_threshold")) {
            String nodeId = name.replace("_threshold", "");
            if (topology.nodes().containsKey(nodeId)) {
                NodeState current = nodeStates.getOrDefault(nodeId, NodeState.DEFAULT);
                nodeStates.put(nodeId, new NodeState(
                    current.activationThreshold() * multiplier,
                    current.restingActivation()));
                return;
            }
        }

        if (name.startsWith("_")) return;

        if (topology.nodes().containsKey(name)) {
            CapsNode node = topology.nodes().get(name);
            for (CapsConnection conn : topology.connections()) {
                if (node.type() == NodeType.OUTPUT && conn.to().equals(name)) {
                    multiplyWeight(conn.id(), multiplier, weights);
                } else if (node.type() == NodeType.INPUT && conn.from().equals(name)) {
                    multiplyWeight(conn.id(), multiplier, weights);
                }
            }
            return;
        }

        LOG.warning("Unresolvable disposition modifier: " + name +
            " (multiplier=" + multiplier + "). Expected for topology extensions.");
    }

    private void multiplyWeight(String connId, double multiplier,
                                 Map<String, ConnectionWeight> weights) {
        ConnectionWeight cw = weights.get(connId);
        if (cw == null) return;
        weights.put(connId, new ConnectionWeight(
            cw.excitatory() * multiplier, cw.inhibitory(),
            cw.decayResistance(), cw.precision()));
    }

    // Resting activations as persistent personality baseline — Read et al. (2010):
    // "baseline activation of specific motives" differentiates personality;
    // Gray (1982) BIS/BAS sensitivity maps disposition to approach/avoidance systems
    private void setRestingActivations(DispositionAxes disposition,
                                       Map<String, NodeState> nodeStates) {
        // MEDIATING nodes — tonic sensitivity (Gray 1982)
        if ("cooperative".equals(disposition.socialOrient())) {
            addResting(nodeStates, "other_reliability", 0.3);
            addResting(nodeStates, "self_worth", 0.2);
        } else if ("competitive".equals(disposition.socialOrient())) {
            addResting(nodeStates, "social_comparison", 0.3);
            addResting(nodeStates, "BAS_activation", 0.2);
        }
        if ("bold".equals(disposition.riskAppetite())) {
            addResting(nodeStates, "BAS_activation", 0.3);
        } else if ("conservative".equals(disposition.riskAppetite())) {
            addResting(nodeStates, "BIS_activation", 0.3);
        }
        if ("strict".equals(disposition.ruleFollowing())) {
            addResting(nodeStates, "reinforcement_expectation", 0.2);
        } else if ("flexible".equals(disposition.ruleFollowing())) {
            addResting(nodeStates, "self_efficacy", 0.15);
        }
        if ("independent".equals(disposition.autonomy())) {
            addResting(nodeStates, "self_efficacy", 0.2);
        } else if ("dependent".equals(disposition.autonomy())) {
            addResting(nodeStates, "other_reliability", 0.15);
        }
        if ("confrontational".equals(disposition.conflictMode())) {
            addResting(nodeStates, "threat_sensitivity", 0.15);
        } else if ("avoidant".equals(disposition.conflictMode())) {
            addResting(nodeStates, "escape_assessment", 0.2);
        }

        // OUTPUT nodes — attractor baselines from disposition (Read et al. 2010)
        if ("competitive".equals(disposition.socialOrient())) {
            addResting(nodeStates, "approach", 0.2);
            addResting(nodeStates, "fight", 0.15);
            addResting(nodeStates, "assert", 0.15);
            addResting(nodeStates, "distrust", 0.1);
        } else if ("cooperative".equals(disposition.socialOrient())) {
            addResting(nodeStates, "cautious_approach", 0.2);
            addResting(nodeStates, "trust", 0.15);
            addResting(nodeStates, "proximity_seek", 0.15);
            addResting(nodeStates, "fawn", 0.1);
        }
        if ("bold".equals(disposition.riskAppetite())) {
            addResting(nodeStates, "approach", 0.15);
            addResting(nodeStates, "risk_take", 0.15);
            addResting(nodeStates, "explore", 0.1);
        } else if ("conservative".equals(disposition.riskAppetite())) {
            addResting(nodeStates, "withdraw", 0.15);
            addResting(nodeStates, "cautious_approach", 0.15);
            addResting(nodeStates, "consolidate", 0.1);
        }
        if ("confrontational".equals(disposition.conflictMode())) {
            addResting(nodeStates, "fight", 0.15);
            addResting(nodeStates, "assert", 0.1);
        } else if ("avoidant".equals(disposition.conflictMode())) {
            addResting(nodeStates, "flight", 0.15);
            addResting(nodeStates, "withdraw", 0.1);
        }
        if ("independent".equals(disposition.autonomy())) {
            addResting(nodeStates, "self_direct", 0.15);
            addResting(nodeStates, "rebel", 0.1);
        } else if ("dependent".equals(disposition.autonomy())) {
            addResting(nodeStates, "depend", 0.15);
            addResting(nodeStates, "comply", 0.1);
        }
    }

    private void addResting(Map<String, NodeState> nodeStates,
                             String nodeId, double delta) {
        NodeState current = nodeStates.getOrDefault(nodeId, NodeState.DEFAULT);
        nodeStates.put(nodeId, new NodeState(
            current.activationThreshold(),
            current.restingActivation() + delta));
    }

    private static Map<String, Set<String>> buildTagIndex(CapsTopology topology) {
        Map<String, Set<String>> index = new HashMap<>();
        for (CapsConnection conn : topology.connections()) {
            for (String tag : conn.tags()) {
                index.computeIfAbsent(tag.toLowerCase(), k -> new HashSet<>())
                    .add(conn.id());
            }
        }
        return Collections.unmodifiableMap(index);
    }
}

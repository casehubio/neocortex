package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.*;
import io.casehub.neocortex.cognitive.index.DispositionAxes;

import java.util.*;
import java.util.logging.Logger;

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
                CapsWeightUpdater.provenancePrecision(conn.provenance())));
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

    private void setRestingActivations(DispositionAxes disposition,
                                        Map<String, NodeState> nodeStates) {
        if ("cooperative".equals(disposition.socialOrient())) {
            setResting(nodeStates, "other_reliability", 0.2);
        }
        if ("bold".equals(disposition.riskAppetite())) {
            setResting(nodeStates, "BAS_activation", 0.1);
        }
        if ("conservative".equals(disposition.riskAppetite())) {
            setResting(nodeStates, "BIS_activation", 0.1);
        }
    }

    private void setResting(Map<String, NodeState> nodeStates,
                             String nodeId, double resting) {
        NodeState current = nodeStates.getOrDefault(nodeId, NodeState.DEFAULT);
        nodeStates.put(nodeId, new NodeState(
            current.activationThreshold(), resting));
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

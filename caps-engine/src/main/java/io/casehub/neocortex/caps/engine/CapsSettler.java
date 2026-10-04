package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.*;
import java.util.*;

public class CapsSettler {

    private final CapsTopology topology;

    public CapsSettler(CapsTopology topology) {
        this.topology = topology;
    }

    public SettlingResult settle(AgentCapsState state,
                                 Map<String, Double> inputActivations) {
        var params = topology.weightUpdate();
        Map<String, Double> activations = new HashMap<>();

        for (var entry : topology.nodes().entrySet()) {
            CapsNode node = entry.getValue();
            if (node.type() == NodeType.INPUT) {
                activations.put(node.id(),
                    inputActivations.getOrDefault(node.id(), 0.0));
            } else {
                NodeState ns = state.nodeStates()
                    .getOrDefault(node.id(), NodeState.DEFAULT);
                activations.put(node.id(), ns.restingActivation());
            }
        }

        Map<String, List<CapsConnection>> incomingByNode = new HashMap<>();
        for (CapsConnection conn : topology.connections()) {
            incomingByNode.computeIfAbsent(conn.to(), k -> new ArrayList<>())
                .add(conn);
        }

        Map<String, Double> prevPrev = null;
        List<Map<String, Double>> lastTen = new ArrayList<>();
        int iterations = 0;

        for (int iter = 0; iter < params.maxIterations(); iter++) {
            iterations = iter + 1;
            Map<String, Double> newActivations = new HashMap<>(activations);

            for (var entry : topology.nodes().entrySet()) {
                CapsNode node = entry.getValue();
                if (node.type() == NodeType.INPUT) continue;

                List<CapsConnection> incoming = incomingByNode
                    .getOrDefault(node.id(), List.of());
                double netInput = 0.0;
                for (CapsConnection conn : incoming) {
                    ConnectionWeight cw = state.weights()
                        .getOrDefault(conn.id(),
                            new ConnectionWeight(conn.defaultWeight(), 0.0, 1.0, 1.0));
                    double sourceActivation = activations
                        .getOrDefault(conn.from(), 0.0);
                    netInput += sourceActivation * cw.effectiveWeight();
                }

                netInput = applyDistortions(node, activations.get(node.id()),
                    netInput, state);

                NodeState ns = state.nodeStates()
                    .getOrDefault(node.id(), NodeState.DEFAULT);

                double newAct;
                if (node.range() == NodeRange.BIPOLAR) {
                    newAct = Math.tanh(netInput - ns.activationThreshold());
                } else {
                    newAct = logistic(netInput - ns.activationThreshold());
                }

                newActivations.put(node.id(), newAct);
            }

            double maxDelta = 0.0;
            for (var entry : topology.nodes().entrySet()) {
                if (entry.getValue().type() == NodeType.INPUT) continue;
                double delta = Math.abs(
                    newActivations.get(entry.getKey()) -
                    activations.get(entry.getKey()));
                maxDelta = Math.max(maxDelta, delta);
            }

            if (prevPrev != null) {
                double osc = 0.0;
                for (var entry : topology.nodes().entrySet()) {
                    if (entry.getValue().type() == NodeType.INPUT) continue;
                    osc = Math.max(osc, Math.abs(
                        newActivations.get(entry.getKey()) -
                        prevPrev.get(entry.getKey())));
                }
                if (osc < params.epsilon()) {
                    return buildResult(newActivations, activations,
                        iterations, ConvergenceType.OSCILLATION, state);
                }
            }

            prevPrev = activations;
            activations = newActivations;

            if (lastTen.size() >= 10) lastTen.removeFirst();
            lastTen.add(Map.copyOf(activations));

            if (maxDelta < params.epsilon()) {
                return buildResult(activations, null,
                    iterations, ConvergenceType.CONVERGED, state);
            }
        }

        Map<String, Double> bestState = findMostSettled(lastTen);
        return buildResult(bestState, null,
            iterations, ConvergenceType.MAX_ITERATIONS, state);
    }

    private double applyDistortions(CapsNode node, double currentActivation,
                                     double netInput, AgentCapsState state) {
        double compoundMultiplier = 1.0;
        double additiveTotal = 0.0;

        for (DistortionDefinition dist : topology.distortions()) {
            if (!dist.targetCategories().contains(node.category())) continue;

            double effectiveThreshold = dist.baseThreshold() * (
                1.0 - dist.negativeWeight() * Math.max(0, -currentActivation)
                    - dist.positiveWeight() * Math.max(0, currentActivation));
            effectiveThreshold = Math.max(0.05, effectiveThreshold);

            if (Math.abs(currentActivation) <= effectiveThreshold) continue;

            double t = (Math.abs(currentActivation) - effectiveThreshold)
                       / (1.0 - effectiveThreshold);
            t = Math.max(0, Math.min(1, t));
            double mult = dist.multiplierMin() + t * (dist.multiplierMax() - dist.multiplierMin());

            if (dist.effect() == DistortionEffect.MULTIPLICATIVE) {
                compoundMultiplier *= mult;
            } else {
                additiveTotal += mult;
            }
        }

        var cap = topology.weightUpdate().compoundDistortionCap();
        compoundMultiplier = Math.max(cap[0], Math.min(cap[1], compoundMultiplier));

        double result = netInput * compoundMultiplier + additiveTotal;
        var wCap = topology.weightUpdate().effectiveWeightCap();
        return Math.max(wCap[0], Math.min(wCap[1], result));
    }

    private double logistic(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private SettlingResult buildResult(Map<String, Double> primaryState,
                                       Map<String, Double> secondaryState,
                                       int iterations,
                                       ConvergenceType convergence,
                                       AgentCapsState agentState) {
        double saturationRatio = computeSaturation(primaryState);
        boolean highSat = saturationRatio > topology.weightUpdate().saturationWarningThreshold();

        List<BehavioralAttractor> attractors = new ArrayList<>();
        extractAttractors(primaryState, attractors, highSat,
            agentState.generation(), convergence == ConvergenceType.OSCILLATION ? 0.5 : 1.0);
        if (secondaryState != null && convergence == ConvergenceType.OSCILLATION) {
            extractAttractors(secondaryState, attractors, highSat,
                agentState.generation(), 0.5);
        }

        return new SettlingResult(primaryState, iterations,
            convergence, saturationRatio, List.copyOf(attractors));
    }

    private void extractAttractors(Map<String, Double> activations,
                                    List<BehavioralAttractor> attractors,
                                    boolean highSat, long generation,
                                    double strengthMultiplier) {
        for (var entry : topology.nodes().entrySet()) {
            CapsNode node = entry.getValue();
            if (node.type() != NodeType.OUTPUT) continue;
            double strength = activations.getOrDefault(node.id(), 0.0) * strengthMultiplier;
            if (strength > 0.1) {
                attractors.add(new BehavioralAttractor(
                    node.category(), node.id(), strength, highSat, generation));
            }
        }
    }

    private double computeSaturation(Map<String, Double> activations) {
        long mediating = topology.nodes().values().stream()
            .filter(n -> n.type() == NodeType.MEDIATING).count();
        if (mediating == 0) return 0;
        long saturated = topology.nodes().values().stream()
            .filter(n -> n.type() == NodeType.MEDIATING)
            .filter(n -> Math.abs(activations.getOrDefault(n.id(), 0.0)) > 0.99)
            .count();
        return (double) saturated / mediating;
    }

    private Map<String, Double> findMostSettled(List<Map<String, Double>> states) {
        if (states.size() < 2) return states.getLast();
        Map<String, Double> best = states.getLast();
        double bestMaxDelta = Double.MAX_VALUE;
        for (int i = 1; i < states.size(); i++) {
            double maxDelta = 0;
            for (var key : states.get(i).keySet()) {
                maxDelta = Math.max(maxDelta,
                    Math.abs(states.get(i).get(key) - states.get(i - 1).get(key)));
            }
            if (maxDelta < bestMaxDelta) {
                bestMaxDelta = maxDelta;
                best = states.get(i);
            }
        }
        return best;
    }
}

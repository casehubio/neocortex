package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.*;
import java.util.*;

public class CapsWeightUpdater {

    private static final double PRECISION_GROWTH_RATE = 0.1;
    private static final double PRECISION_DECAY_FACTOR = 0.9;
    private static final double CONFIRM_EPSILON = 0.05;
    private static final double BASE_DECAY_RATE = 0.05;
    private static final double MIN_PRECISION = 0.1;

    private final CapsTopology topology;

    public CapsWeightUpdater(CapsTopology topology) {
        this.topology = topology;
    }

    public AgentCapsState update(AgentCapsState state,
                                  Map<String, Double> inputActivations,
                                  double outcomeIntensity,
                                  double outcomeValence,
                                  double salienceMultiplier,
                                  String reinforcementSchedule) {
        var params = topology.weightUpdate();
        double scheduleMod = params.scheduleModifiers()
            .getOrDefault(reinforcementSchedule, 1.0);

        Map<String, ConnectionWeight> newWeights = new HashMap<>(state.weights());

        for (CapsConnection conn : topology.connections()) {
            double sourceActivation = inputActivations
                .getOrDefault(conn.from(), 0.0);
            if (Math.abs(sourceActivation) < 0.01) continue;

            ConnectionWeight cw = state.weights()
                .getOrDefault(conn.id(),
                    new ConnectionWeight(conn.defaultWeight(), 0.0, 1.0,
                        provenancePrecision(conn.provenance())));

            double predicted = cw.effectiveWeight() * sourceActivation;
            double predictionError = outcomeValence - predicted;

            double baseAlpha = Math.max(params.alphaMin(),
                Math.min(params.alphaMax(), salienceMultiplier * 0.1));
            double effectiveAlpha = baseAlpha / cw.precision();
            double beta = Math.max(params.betaMin(),
                Math.min(params.betaMax(), outcomeIntensity));

            double deltaW = effectiveAlpha * beta * predictionError * scheduleMod;
            double newExcitatory = cw.excitatory() + deltaW;

            double newPrecision;
            if (Math.abs(predictionError) < CONFIRM_EPSILON) {
                newPrecision = cw.precision() + PRECISION_GROWTH_RATE;
            } else {
                newPrecision = Math.max(MIN_PRECISION,
                    cw.precision() * PRECISION_DECAY_FACTOR);
            }

            double effectiveResistance = 1.0 + (cw.decayResistance() - 1.0) * 0.5;
            double newInhibitory = cw.inhibitory() *
                (1.0 - BASE_DECAY_RATE / effectiveResistance);

            newWeights.put(conn.id(),
                new ConnectionWeight(newExcitatory, newInhibitory,
                    cw.decayResistance(), newPrecision));
        }

        return new AgentCapsState(state.agentId(), state.tenantId(),
            state.generation(), Map.copyOf(newWeights), state.nodeStates());
    }

    static double provenancePrecision(WeightProvenance provenance) {
        return switch (provenance) {
            case EMPIRICAL -> 10.0;
            case CONSENSUS -> 5.0;
            case ESTIMATED -> 1.0;
        };
    }
}

package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.AgentCapsState;
import io.casehub.neocortex.caps.CapsConnection;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.ConnectionWeight;
import io.casehub.neocortex.caps.WeightProvenance;
import io.casehub.neocortex.caps.WeightUpdateContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CapsWeightUpdater {

    private final CapsTopology           topology;
    private final CapsWeightUpdateConfig config;

    public CapsWeightUpdater(CapsTopology topology) {
        this(topology, CapsWeightUpdateConfig.defaults());
    }

    public CapsWeightUpdater(CapsTopology topology, CapsWeightUpdateConfig config) {
        this.topology = topology;
        this.config   = config;
    }

    /**
     * Updates CAPS connection weights using Rescorla-Wagner (1972) prediction-error learning
     * with Pearce-Hall (1980) precision modulation.
     *
     * <p>Cattell's (1971) Investment Theory: {@link WeightUpdateContext#capacityMultiplier()}
     * (sourced from {@code associativeReach}, Mednick 1962) scales the base alpha before
     * precision division &mdash; higher associative capacity strengthens stimulus-response
     * learning speed.
     */
    public AgentCapsState update(AgentCapsState state, WeightUpdateContext context) {
        var params = topology.weightUpdate();
        double scheduleMod = params.scheduleModifiers()
                                   .getOrDefault(context.reinforcementSchedule(), 1.0);

        Map<String, ConnectionWeight> newWeights = new HashMap<>(state.weights());

        Map<String, List<CapsConnection>> byTarget = topology.connections().stream()
                                                             .collect(java.util.stream.Collectors.groupingBy(CapsConnection::to));

        for (var entry : byTarget.entrySet()) {
            List<CapsConnection> incoming = entry.getValue();

            double vPredicted = 0.0;
            for (CapsConnection conn : incoming) {
                double sourceAct = context.inputActivations()
                                          .getOrDefault(conn.from(), 0.0);
                if (Math.abs(sourceAct) < 0.01) {continue;}
                ConnectionWeight cw = state.weights()
                                           .getOrDefault(conn.id(),
                                                         new ConnectionWeight(conn.defaultWeight(),
                                                                              provenancePrecision(conn.provenance())));
                vPredicted += cw.effectiveWeight() * sourceAct;
            }

            double delta = context.outcomeValence() - vPredicted;

            for (CapsConnection conn : incoming) {
                double sourceAct = context.inputActivations()
                                          .getOrDefault(conn.from(), 0.0);
                if (Math.abs(sourceAct) < 0.01) {continue;}

                ConnectionWeight cw = state.weights()
                                           .getOrDefault(conn.id(),
                                                         new ConnectionWeight(conn.defaultWeight(),
                                                                              provenancePrecision(conn.provenance())));

                double baseAlpha = Math.max(params.alphaMin(),
                                            Math.min(params.alphaMax(),
                                                     context.salienceMultiplier() * config.salienceAlphaScaling()));
                baseAlpha *= context.capacityMultiplier();
                double effectiveAlpha = baseAlpha / cw.precision();
                double beta = Math.max(params.betaMin(),
                                       Math.min(params.betaMax(), context.outcomeIntensity()));

                double deltaW = effectiveAlpha * beta * delta * scheduleMod;
                double newExcitatory = Math.max(-2.0, Math.min(2.0,
                                                               cw.excitatory() + deltaW));

                double newPrecision;
                if (Math.abs(delta) < config.confirmEpsilon()) {
                    newPrecision = cw.precision() + config.precisionGrowthRate();
                } else {
                    newPrecision = Math.max(config.minPrecision(),
                                            cw.precision() * config.precisionDecayFactor());
                }

                newWeights.put(conn.id(),
                               new ConnectionWeight(newExcitatory, newPrecision));
            }
        }

        return new AgentCapsState(state.agentId(), state.tenantId(),
                                  state.generation(), Map.copyOf(newWeights), state.nodeStates());
    }

    double provenancePrecision(WeightProvenance provenance) {
        return config.provenancePrecision(provenance);
    }
}

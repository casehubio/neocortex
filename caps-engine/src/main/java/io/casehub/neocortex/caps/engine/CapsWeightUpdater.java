package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.AgentCapsState;
import io.casehub.neocortex.caps.CapsConnection;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.ConnectionWeight;
import io.casehub.neocortex.caps.WeightProvenance;
import io.casehub.neocortex.caps.WeightUpdateContext;

import java.util.HashMap;
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

        for (CapsConnection conn : topology.connections()) {
            double sourceActivation = context.inputActivations()
                                              .getOrDefault(conn.from(), 0.0);
            if (Math.abs(sourceActivation) < 0.01) {continue;}

            ConnectionWeight cw = state.weights()
                                       .getOrDefault(conn.id(),
                                                     new ConnectionWeight(conn.defaultWeight(), 0.0, 1.0,
                                                                          provenancePrecision(conn.provenance())));

            double predicted       = cw.effectiveWeight() * sourceActivation;
            double predictionError = context.outcomeValence() - predicted;

            double baseAlpha = Math.max(params.alphaMin(),
                                        Math.min(params.alphaMax(),
                                                 context.salienceMultiplier() * config.salienceAlphaScaling()));
            // Cattell (1971): associativeReach modulates associative learning rate
            baseAlpha *= context.capacityMultiplier();
            double effectiveAlpha = baseAlpha / cw.precision();
            double beta = Math.max(params.betaMin(),
                                   Math.min(params.betaMax(), context.outcomeIntensity()));

            double deltaW        = effectiveAlpha * beta * predictionError * scheduleMod;
            double newExcitatory = cw.excitatory() + deltaW;

            double newPrecision;
            if (Math.abs(predictionError) < config.confirmEpsilon()) {
                newPrecision = cw.precision() + config.precisionGrowthRate();
            } else {
                newPrecision = Math.max(config.minPrecision(),
                                        cw.precision() * config.precisionDecayFactor());
            }

            double effectiveResistance = 1.0 + (cw.decayResistance() - 1.0) * config.resistanceDampening();
            double newInhibitory = cw.inhibitory() *
                                   (1.0 - config.baseDecayRate() / effectiveResistance);

            newWeights.put(conn.id(),
                           new ConnectionWeight(newExcitatory, newInhibitory,
                                                cw.decayResistance(), newPrecision));
        }

        return new AgentCapsState(state.agentId(), state.tenantId(),
                                  state.generation(), Map.copyOf(newWeights), state.nodeStates());
    }

    double provenancePrecision(WeightProvenance provenance) {
        return config.provenancePrecision(provenance);
    }
}

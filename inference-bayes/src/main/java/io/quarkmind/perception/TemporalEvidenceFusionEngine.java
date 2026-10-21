package io.quarkmind.perception;

import io.quarkmind.inference.bayes.BayesInstance;
import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.BayesVariable;
import io.quarkmind.inference.bayes.BayesVariableState;
import io.quarkmind.inference.bayes.JunctionTree;
import io.quarkmind.inference.bayes.JunctionTreeBuilder;
import io.quarkmind.inference.bayes.graph.GraphNode;

import java.util.Map;

/**
 * Temporal extension of EvidenceFusionEngine — carries posteriors across ticks
 * as temporal priors with configurable decay. Prevents single-tick classification
 * flips by smoothing evidence over time.
 */
public class TemporalEvidenceFusionEngine {

    private JunctionTree network;
    private BayesInstance<?> instance;
    private String targetVariable;
    private String[] outcomeNames;
    private final java.util.Set<String> activeEvidence = new java.util.HashSet<>();
    private double[] previousPosterior;
    private double temporalDecay;
    private int tickCount;

    public void initialize(BayesNetwork graph, String targetVariable, double temporalDecay) {
        if (temporalDecay < 0.0 || temporalDecay > 1.0) {
            throw new IllegalArgumentException("temporalDecay must be in [0.0, 1.0], got " + temporalDecay);
        }
        this.targetVariable = targetVariable;
        this.temporalDecay = temporalDecay;
        this.network = new JunctionTreeBuilder(graph).build();
        this.instance = new BayesInstance<>(network, true);
        this.tickCount = 0;

        for (GraphNode<BayesVariable> node : graph) {
            if (node.getContent().getName().equals(targetVariable)) {
                this.outcomeNames = (String[]) node.getContent().getOutcomes();
                break;
            }
        }
    }

    public void setEvidence(String variable, double[] likelihood) {
        instance.setLikelyhood(variable, likelihood);
        activeEvidence.add(variable);
    }

    public void setHardEvidence(String variable, String state) {
        instance.setHardEvidence(variable, state);
        activeEvidence.add(variable);
    }

    public void setAllEvidence(Map<String, double[]> evidence) {
        instance.batchSetEvidence(evidence);
        activeEvidence.clear();
        activeEvidence.addAll(evidence.keySet());
    }

    /**
     * Update and advance the time slice. Injects temporal prior from previous
     * tick (if any), propagates, saves posterior for next tick.
     */
    public void update() {
        instance.globalUpdate();
        double[] currentPosterior = instance.marginalize(targetVariable).getDistribution().clone();
        if (previousPosterior != null && temporalDecay > 0.0) {
            for (int i = 0; i < currentPosterior.length; i++) {
                currentPosterior[i] = (1.0 - temporalDecay) * currentPosterior[i]
                                      + temporalDecay * previousPosterior[i];
            }
        }
        previousPosterior = currentPosterior;
        tickCount++;
    }

    public double[] marginalize() {
        if (previousPosterior == null) {
            BayesVariableState state = instance.marginalize(targetVariable);
            return state.getDistribution();
        }
        return previousPosterior.clone();
    }

    public String[] getOutcomeNames() {
        return outcomeNames.clone();
    }

    public int getTickCount() {
        return tickCount;
    }

    public double[] getPreviousPosterior() {
        return previousPosterior != null ? previousPosterior.clone() : null;
    }

    public double getTemporalDecay() {
        return temporalDecay;
    }

    public void setTemporalDecay(double temporalDecay) {
        if (temporalDecay < 0.0 || temporalDecay > 1.0) {
            throw new IllegalArgumentException("temporalDecay must be in [0.0, 1.0], got " + temporalDecay);
        }
        this.temporalDecay = temporalDecay;
    }

    public void reset() {
        for (String variable : activeEvidence) {
            instance.unsetLikelyhood(variable);
        }
        activeEvidence.clear();
        previousPosterior = null;
        tickCount = 0;
    }

    /**
     * Blend a distribution toward uniform by a decay factor.
     * decay=1.0 means full carry-over (no decay); decay=0.0 means pure uniform (full decay).
     * Result: decay * dist + (1-decay) * uniform
     */
    static double[] blendWithUniform(double[] dist, double decay) {
        int n = dist.length;
        double uniform = 1.0 / n;
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            result[i] = decay * dist[i] + (1.0 - decay) * uniform;
        }
        return result;
    }
}

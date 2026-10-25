package io.quarkmind.perception;

import io.quarkmind.inference.bayes.BayesInstance;
import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.BayesVariable;
import io.quarkmind.inference.bayes.BayesVariableState;
import io.quarkmind.inference.bayes.ExplanationEngine;
import io.quarkmind.inference.bayes.ExplanationTrace;
import io.quarkmind.inference.bayes.JunctionTree;
import io.quarkmind.inference.bayes.JunctionTreeBuilder;
import io.quarkmind.inference.bayes.MapAssignment;
import io.quarkmind.inference.bayes.graph.GraphNode;

public class EvidenceFusionEngine {

    private       BayesNetwork          graph;
    private       JunctionTree          network;
    private       BayesInstance<?>      instance;
    private       String                targetVariable;
    private       String[]              outcomeNames;
    private final java.util.Set<String> activeEvidence = new java.util.HashSet<>();

    public void initialize(BayesNetwork graph, String targetVariable) {
        this.graph          = graph;
        this.targetVariable = targetVariable;
        this.network        = new JunctionTreeBuilder(graph).build();
        this.instance       = new BayesInstance<>(network, true);

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

    public void setAllEvidence(java.util.Map<String, double[]> evidence) {
        instance.batchSetEvidence(evidence);
        activeEvidence.clear();
        activeEvidence.addAll(evidence.keySet());
    }


    public void update() {
        instance.globalUpdate();
    }

    public double[] marginalize() {
        BayesVariableState state = instance.marginalize(targetVariable);
        return state.getDistribution().clone();
    }

    public MapAssignment mapInference() {
        return instance.mapInference();
    }

    public ExplanationTrace explain() {
        return ExplanationEngine.explain(graph, instance, targetVariable);
    }


    public java.util.Map<String, Double> getVariableSensitivities() {
        java.util.Map<String, Double>                       sensitivities = new java.util.LinkedHashMap<>();
        java.util.List<java.util.Map.Entry<String, Double>> entries       = new java.util.ArrayList<>();
        for (String variable : activeEvidence) {
            entries.add(java.util.Map.entry(variable, instance.sensitivity(variable, targetVariable)));
        }
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        for (var entry : entries) {
            sensitivities.put(entry.getKey(), entry.getValue());
        }
        return sensitivities;
    }


    public String[] getOutcomeNames() {
        return outcomeNames.clone();
    }

    public io.quarkmind.inference.bayes.BayesStats getStats() {
        return instance.getStats();
    }


    public void reset() {
        for (String variable : activeEvidence) {
            instance.unsetLikelyhood(variable);
        }
        activeEvidence.clear();
    }
}

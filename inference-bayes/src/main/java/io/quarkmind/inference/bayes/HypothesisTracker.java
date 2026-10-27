package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HypothesisTracker {

    private final NetworkComposition composition;
    private final List<HypothesisDefinition> definitions;
    private final double eliminationThreshold;
    private final double resolutionThreshold;
    private Map<String, CompositionInstance> compositions;
    private Map<String, Double> currentWeights;
    private Map<String, HypothesisStatus> statuses;
    private int observationCount;

    private HypothesisTracker(NetworkComposition composition,
                              List<HypothesisDefinition> definitions,
                              double eliminationThreshold,
                              double resolutionThreshold) {
        this.composition = composition;
        this.definitions = List.copyOf(definitions);
        this.eliminationThreshold = eliminationThreshold;
        this.resolutionThreshold = resolutionThreshold;
        initializeFromDefinitions();
    }

    private void initializeFromDefinitions() {
        compositions = new LinkedHashMap<>();
        currentWeights = new LinkedHashMap<>();
        statuses = new LinkedHashMap<>();
        observationCount = 0;

        for (var def : definitions) {
            var instance = new CompositionInstance(composition);

            for (var activation : def.variantActivations().entrySet()) {
                instance.activate(activation.getKey(), activation.getValue());
            }

            for (var override : def.priorOverrides().entrySet()) {
                String key = override.getKey();
                int dot = key.indexOf('.');
                if (dot < 0) {
                    throw new IllegalArgumentException(
                        "Prior override key must be 'slot.variable': " + key);
                }
                String slot = key.substring(0, dot);
                String var = key.substring(dot + 1);
                instance.setEvidence(slot, var, override.getValue().clone());
            }

            instance.update();
            compositions.put(def.name(), instance);
            currentWeights.put(def.name(), def.priorWeight());
            statuses.put(def.name(), HypothesisStatus.VIABLE);
        }
    }

    public static HypothesisTrackerBuilder builder(NetworkComposition composition) {
        return new HypothesisTrackerBuilder(composition);
    }

    public ObservationResult observe(String slotName, String variableName,
                                     double[] distribution) {
        List<String> eliminatedThisStep = new ArrayList<>();

        for (var entry : compositions.entrySet()) {
            String name = entry.getKey();
            if (statuses.get(name) != HypothesisStatus.VIABLE) continue;

            CompositionInstance comp = entry.getValue();
            double[] marginal = comp.marginalize(slotName, variableName);

            double likelihood = 0;
            for (int j = 0; j < distribution.length; j++) {
                likelihood += distribution[j] * marginal[j];
            }

            if (likelihood == 0.0) {
                statuses.put(name, HypothesisStatus.ELIMINATED);
                eliminatedThisStep.add(name);
                continue;
            }

            currentWeights.put(name, currentWeights.get(name) * likelihood);
            comp.setEvidence(slotName, variableName, distribution);
            comp.update();
        }

        normalize();
        softEliminate(eliminatedThisStep);
        if (!eliminatedThisStep.isEmpty()) normalize();
        observationCount++;
        return buildResult(eliminatedThisStep);
    }

    public ObservationResult observeHard(String slotName, String variableName,
                                         String state) {
        List<String> eliminatedThisStep = new ArrayList<>();

        int stateIndex = resolveStateIndex(slotName, variableName, state);

        for (var entry : compositions.entrySet()) {
            String name = entry.getKey();
            if (statuses.get(name) != HypothesisStatus.VIABLE) {continue;}

            CompositionInstance comp     = entry.getValue();
            double[]            marginal = comp.marginalize(slotName, variableName);

            double likelihood = marginal[stateIndex];

            if (likelihood == 0.0) {
                statuses.put(name, HypothesisStatus.ELIMINATED);
                eliminatedThisStep.add(name);
                continue;
            }

            currentWeights.put(name, currentWeights.get(name) * likelihood);
            comp.setHardEvidence(slotName, variableName, state);
            comp.update();
        }

        normalize();
        softEliminate(eliminatedThisStep);
        if (!eliminatedThisStep.isEmpty()) {normalize();}
        observationCount++;
        return buildResult(eliminatedThisStep);
    }

    public Map<String, Double> weights() {
        return Map.copyOf(currentWeights);
    }

    public List<String> viable() {
        List<String> result = new ArrayList<>();
        for (var entry : statuses.entrySet()) {
            if (entry.getValue() == HypothesisStatus.VIABLE) {
                result.add(entry.getKey());
            }
        }
        return List.copyOf(result);
    }

    public String leader() {
        String best = null;
        double bestWeight = -1;
        for (var entry : currentWeights.entrySet()) {
            if (statuses.get(entry.getKey()) != HypothesisStatus.VIABLE) continue;
            if (entry.getValue() > bestWeight) {
                bestWeight = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    public boolean isResolved() {
        String leader = leader();
        if (leader == null) return false;
        return currentWeights.get(leader) >= resolutionThreshold;
    }

    public TrackerSnapshot snapshot() {
        return new TrackerSnapshot(
            Map.copyOf(currentWeights),
            Map.copyOf(statuses),
            leader(),
            computeEntropy(),
            observationCount,
            viable()
        );
    }

    public void reset() {
        initializeFromDefinitions();
    }

    public void setPerTickEvidence(String slotName, String variableName, double[] distribution) {
        for (var entry : compositions.entrySet()) {
            if (statuses.get(entry.getKey()) == HypothesisStatus.VIABLE) {
                entry.getValue().setEvidence(slotName, variableName, distribution);
                entry.getValue().update();
            }
        }
    }

    public void clearPerTickEvidence(String slotName, String variableName) {
        for (var entry : compositions.entrySet()) {
            if (statuses.get(entry.getKey()) == HypothesisStatus.VIABLE) {
                entry.getValue().unsetEvidence(slotName, variableName);
                entry.getValue().update();
            }
        }
    }

    public double[] marginalize(String hypothesisName, String slotName, String variableName) {
        CompositionInstance comp = compositions.get(hypothesisName);
        if (comp == null) {
            throw new IllegalArgumentException("Unknown hypothesis: " + hypothesisName);
        }
        return comp.marginalize(slotName, variableName);
    }


    private double computeEntropy() {
        double entropy = 0;
        for (var entry : currentWeights.entrySet()) {
            if (statuses.get(entry.getKey()) != HypothesisStatus.VIABLE) continue;
            double w = entry.getValue();
            if (w > 0) {
                entropy -= w * (Math.log(w) / Math.log(2));
            }
        }
        return entropy;
    }

    private void normalize() {
        double sum = 0;
        for (var entry : currentWeights.entrySet()) {
            if (statuses.get(entry.getKey()) == HypothesisStatus.VIABLE) {
                sum += entry.getValue();
            }
        }
        if (sum == 0) {
            throw new IllegalStateException(
                "All hypothesis weights are zero — hypothesis set does not cover the observation space");
        }
        for (var entry : currentWeights.entrySet()) {
            if (statuses.get(entry.getKey()) == HypothesisStatus.VIABLE) {
                currentWeights.put(entry.getKey(), entry.getValue() / sum);
            }
        }
    }

    private void softEliminate(List<String> eliminatedThisStep) {
        for (var entry : new ArrayList<>(currentWeights.entrySet())) {
            String name = entry.getKey();
            if (statuses.get(name) != HypothesisStatus.VIABLE) continue;
            if (entry.getValue() < eliminationThreshold) {
                statuses.put(name, HypothesisStatus.ELIMINATED);
                eliminatedThisStep.add(name);
            }
        }
    }

    private int resolveStateIndex(String slotName, String variableName, String state) {
        CompositionInstance        first = compositions.values().iterator().next();
        Map<String, BayesVariable> vars  = first.getVariables(slotName);
        BayesVariable              var   = vars.get(variableName);
        if (var == null) {
            throw new IllegalArgumentException(
                    "Variable '" + variableName + "' not found in slot '" + slotName + "'");
        }
        Object[] outcomes = var.getOutcomes();
        for (int i = 0; i < outcomes.length; i++) {
            if (state.equals(outcomes[i])) {return i;}
        }
        throw new IllegalArgumentException(
                "State '" + state + "' not found for variable '" + variableName + "'");
    }


    private ObservationResult buildResult(List<String> eliminatedThisStep) {
        return new ObservationResult(
            Map.copyOf(currentWeights),
            eliminatedThisStep,
            leader(),
            computeEntropy(),
            viable(),
            isResolved()
        );
    }

    // --- Builder ---

    public static class HypothesisTrackerBuilder {

        private final NetworkComposition composition;
        private final List<HypothesisDefBuilder> hypotheses = new ArrayList<>();
        private double eliminationThreshold = 0.01;
        private double resolutionThreshold = 0.95;

        HypothesisTrackerBuilder(NetworkComposition composition) {
            if (composition == null) {
                throw new IllegalArgumentException("Composition must not be null");
            }
            this.composition = composition;
        }

        public HypothesisDefBuilder hypothesis(String name) {
            var hdb = new HypothesisDefBuilder(this, name);
            hypotheses.add(hdb);
            return hdb;
        }

        public HypothesisTrackerBuilder eliminationThreshold(double threshold) {
            this.eliminationThreshold = threshold;
            return this;
        }

        public HypothesisTrackerBuilder resolutionThreshold(double threshold) {
            this.resolutionThreshold = threshold;
            return this;
        }

        public HypothesisTracker build() {
            if (hypotheses.isEmpty()) {
                throw new IllegalArgumentException("At least one hypothesis is required");
            }
            List<HypothesisDefinition> defs = new ArrayList<>();
            var names = new HashSet<String>();
            for (var hdb : hypotheses) {
                if (!names.add(hdb.name)) {
                    throw new IllegalArgumentException("Duplicate hypothesis name: " + hdb.name);
                }
                defs.add(new HypothesisDefinition(
                    hdb.name, hdb.priorWeight,
                    hdb.variantActivations, hdb.priorOverrides));
            }
            return new HypothesisTracker(composition, defs,
                eliminationThreshold, resolutionThreshold);
        }

        public static class HypothesisDefBuilder {
            private final HypothesisTrackerBuilder parent;
            final String name;
            double priorWeight = 1.0;
            final Map<String, String> variantActivations = new LinkedHashMap<>();
            final Map<String, double[]> priorOverrides = new LinkedHashMap<>();

            HypothesisDefBuilder(HypothesisTrackerBuilder parent, String name) {
                this.parent = parent;
                this.name = name;
            }

            public HypothesisDefBuilder priorWeight(double weight) {
                this.priorWeight = weight;
                return this;
            }

            public HypothesisDefBuilder activateVariant(String slotName, String contextKey) {
                variantActivations.put(slotName, contextKey);
                return this;
            }

            public HypothesisDefBuilder priorOverride(String slotName, String variableName,
                                                       double[] distribution) {
                priorOverrides.put(slotName + "." + variableName, distribution);
                return this;
            }

            public HypothesisDefBuilder hypothesis(String name) {
                return parent.hypothesis(name);
            }

            public HypothesisTrackerBuilder eliminationThreshold(double threshold) {
                return parent.eliminationThreshold(threshold);
            }

            public HypothesisTrackerBuilder resolutionThreshold(double threshold) {
                return parent.resolutionThreshold(threshold);
            }

            public HypothesisTracker build() {
                return parent.build();
            }
        }
    }
}

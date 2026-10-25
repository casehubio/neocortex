package io.quarkmind.inference.bayes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompositionInstance {

    private final NetworkComposition composition;
    private final Map<String, BayesInstance<?>> instances;
    private final Map<String, Map<String, double[]>> posteriorSnapshots;
    private final Set<String> linkTargetVars;
    private final Set<String> dirtySlots = new HashSet<>();
    private final double epsilon;
    private boolean firstUpdate = true;

    public CompositionInstance(NetworkComposition composition) {
        this(composition, 1e-6);
    }

    public CompositionInstance(NetworkComposition composition, double epsilon) {
        this.composition = composition;
        this.epsilon = epsilon;
        this.instances = new HashMap<>();
        this.posteriorSnapshots = new HashMap<>();
        this.linkTargetVars = new HashSet<>();

        for (var slot : composition.slots()) {
            instances.put(slot.name(), new BayesInstance<>(slot.defaultTree()));
            posteriorSnapshots.put(slot.name(), new HashMap<>());
        }

        for (var link : composition.links()) {
            for (var entry : link.variableMapping().entrySet()) {
                linkTargetVars.add(link.targetSlot() + "." + entry.getValue());
            }
        }
    }

    public void setEvidence(String slotName, String variableName, double[] distribution) {
        guardLinkTarget(slotName, variableName);
        getInstance(slotName).setLikelyhood(variableName, distribution);
    }

    public void setHardEvidence(String slotName, String variableName, String state) {
        guardLinkTarget(slotName, variableName);
        getInstance(slotName).setHardEvidence(variableName, state);
    }

    public void unsetEvidence(String slotName, String variableName) {
        guardLinkTarget(slotName, variableName);
        getInstance(slotName).unsetLikelyhood(variableName);
    }

    public void reset() {
        for (var slot : composition.slots()) {
            BayesInstance<?>      inst     = instances.get(slot.name());
            Map<String, double[]> evidence = inst.getEvidenceVariables();
            for (String varName : evidence.keySet()) {
                if (!linkTargetVars.contains(slot.name() + "." + varName)) {
                    inst.unsetLikelyhood(varName);
                }
            }
            posteriorSnapshots.get(slot.name()).clear();
        }
        firstUpdate = true;
    }

    public CompositionStats update() {
        long startNanos = System.nanoTime();
        int slotsUpdated = 0;
        int slotsSkipped = 0;
        int linksTransferred = 0;

        List<String> order = composition.topologicalOrder();

        for (String slotName : order) {
            BayesInstance<?> inst = instances.get(slotName);
            boolean upstreamChanged = false;

            for (var link : composition.links()) {
                if (!link.targetSlot().equals(slotName)) continue;

                BayesInstance<?> sourceInst = instances.get(link.sourceSlot());
                Map<String, double[]> sourceSnapshots = posteriorSnapshots.get(link.sourceSlot());

                for (var mapping : link.variableMapping().entrySet()) {
                    String sourceVar = mapping.getKey();
                    String targetVar = mapping.getValue();

                    double[] posterior = sourceInst.marginalize(sourceVar).getDistribution().clone();

                    double[] previousSnapshot = sourceSnapshots.get(sourceVar);
                    if (firstUpdate || previousSnapshot == null || !withinEpsilon(posterior, previousSnapshot)) {
                        sourceSnapshots.put(sourceVar, posterior);

                        if (link.transferMode() == TransferMode.MAP) {
                            int argmax = 0;
                            for (int i = 1; i < posterior.length; i++) {
                                if (posterior[i] > posterior[argmax]) argmax = i;
                            }
                            BayesVariable sourceVariable = sourceInst.getVariables().get(sourceVar);
                            String stateName = sourceVariable.getOutcomes()[argmax].toString();
                            inst.setHardEvidence(targetVar, stateName);
                        } else {
                            inst.setLikelyhood(targetVar, posterior.clone());
                        }

                        upstreamChanged = true;
                        linksTransferred++;
                    }
                }
            }

            if (upstreamChanged || firstUpdate || inst.isDirty() || dirtySlots.contains(slotName)) {
                inst.globalUpdate();
                slotsUpdated++;
            } else {
                slotsSkipped++;
            }
        }

        firstUpdate = false;
        dirtySlots.clear();
        long totalNanos = System.nanoTime() - startNanos;
        return new CompositionStats(slotsUpdated, slotsSkipped, linksTransferred, totalNanos);
    }

    public double[] marginalize(String slotName, String variableName) {
        BayesInstance<?> inst = getInstance(slotName);
        return inst.marginalize(variableName).getDistribution().clone();
    }

    public void activate(String slotName, String contextKey) {
        NetworkSlot slot = composition.slotByName(slotName);
        JunctionTree variantTree = slot.variants().get(contextKey);
        if (variantTree == null) {
            throw new IllegalArgumentException(
                "Variant '" + contextKey + "' not found in slot '" + slotName + "'");
        }
        instances.put(slotName, new BayesInstance<>(variantTree));
        posteriorSnapshots.get(slotName).clear();
        markDownstreamDirty(slotName);
    }

    public void activateDefault(String slotName) {
        NetworkSlot slot = composition.slotByName(slotName);
        instances.put(slotName, new BayesInstance<>(slot.defaultTree()));
        posteriorSnapshots.get(slotName).clear();
        markDownstreamDirty(slotName);
    }


    public BayesStats getStats(String slotName) {
        return getInstance(slotName).getStats();
    }

    private BayesInstance<?> getInstance(String slotName) {
        BayesInstance<?> inst = instances.get(slotName);
        if (inst == null) {
            throw new IllegalArgumentException("Slot not found: " + slotName);
        }
        return inst;
    }

    private void guardLinkTarget(String slotName, String variableName) {
        if (!instances.containsKey(slotName)) {
            throw new IllegalArgumentException("Slot not found: " + slotName);
        }
        if (linkTargetVars.contains(slotName + "." + variableName)) {
            throw new IllegalArgumentException(
                "Variable '" + variableName + "' in slot '" + slotName
                + "' is a link target — direct evidence is not allowed");
        }
    }

    private void markDownstreamDirty(String slotName) {
        dirtySlots.add(slotName);
        for (var link : composition.links()) {
            if (link.sourceSlot().equals(slotName) && !dirtySlots.contains(link.targetSlot())) {
                markDownstreamDirty(link.targetSlot());
            }
        }
    }

    private boolean withinEpsilon(double[] a, double[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > epsilon) return false;
        }
        return true;
    }
}

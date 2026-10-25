package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MultiAgentInstance {

    private final MultiAgentComposition composition;
    private final CompositionInstance delegate;

    public MultiAgentInstance(MultiAgentComposition composition) {
        this.composition = composition;
        this.delegate = new CompositionInstance(
            freezeComposition(composition.composition()));
    }

    private static NetworkComposition freezeComposition(NetworkComposition original) {
        List<NetworkSlot> frozenSlots = new ArrayList<>();
        for (var slot : original.slots()) {
            JunctionTree frozenDefault = slot.defaultTree().defensiveCopy().freeze();
            Map<String, JunctionTree> frozenVariants = new LinkedHashMap<>();
            for (var entry : slot.variants().entrySet()) {
                frozenVariants.put(entry.getKey(), entry.getValue().defensiveCopy().freeze());
            }
            frozenSlots.add(new NetworkSlot(slot.name(), frozenDefault, frozenVariants));
        }
        return new NetworkComposition(original.name(), frozenSlots, original.links());
    }

    public void setEvidence(String agent, String variable, double[] distribution) {
        guardCommonKnowledge(agent, variable);
        delegate.setEvidence(agent, variable, distribution);
    }

    public void setHardEvidence(String agent, String variable, String state) {
        guardCommonKnowledge(agent, variable);
        delegate.setHardEvidence(agent, variable, state);
    }

    public void unsetEvidence(String agent, String variable) {
        guardCommonKnowledge(agent, variable);
        delegate.unsetEvidence(agent, variable);
    }

    public void setCommonEvidence(String variable, double[] distribution) {
        guardIsCommonKnowledge(variable);
        for (String agent : composition.agentNames()) {
            delegate.setEvidence(agent, variable, distribution.clone());
        }
    }

    public void setCommonHardEvidence(String variable, String state) {
        guardIsCommonKnowledge(variable);
        for (String agent : composition.agentNames()) {
            delegate.setHardEvidence(agent, variable, state);
        }
    }

    public void unsetCommonEvidence(String variable) {
        guardIsCommonKnowledge(variable);
        for (String agent : composition.agentNames()) {
            delegate.unsetEvidence(agent, variable);
        }
    }

    public CompositionStats update() {
        return delegate.update();
    }

    public double[] marginalize(String agent, String variable) {
        return delegate.marginalize(agent, variable);
    }

    public BeliefDivergence divergence(String agentA, String agentB, String variable) {
        double[] posteriorA = marginalize(agentA, variable);
        double[] posteriorB = marginalize(agentB, variable);
        return Divergences.compute(agentA, agentB, variable, posteriorA, posteriorB);
    }

    public List<BeliefDivergence> allDivergences(String variable) {
        List<String> agents = composition.agentNames();
        List<BeliefDivergence> result = new ArrayList<>();
        for (int i = 0; i < agents.size(); i++) {
            for (int j = i + 1; j < agents.size(); j++) {
                result.add(divergence(agents.get(i), agents.get(j), variable));
            }
        }
        return List.copyOf(result);
    }

    public void activateVariant(String agent, String contextKey) {
        delegate.activate(agent, contextKey);
    }

    public void activateDefault(String agent) {
        delegate.activateDefault(agent);
    }

    public void reset() {
        delegate.reset();
    }

    public List<String> agents() {
        return composition.agentNames();
    }

    public BayesStats getStats(String agent) {
        return delegate.getStats(agent);
    }

    public Map<String, double[]> getEvidenceVariables(String agent) {
        if (!composition.hasAgent(agent)) {
            throw new IllegalArgumentException("Agent not found: " + agent);
        }
        return delegate.getEvidenceVariables(agent);
    }

    private void guardCommonKnowledge(String agent, String variable) {
        if (!composition.hasAgent(agent)) {
            throw new IllegalArgumentException("Agent not found: " + agent);
        }
        if (composition.commonKnowledgeVars().contains(variable)) {
            throw new IllegalArgumentException(
                "Variable '" + variable + "' is common knowledge — use setCommonEvidence() instead");
        }
    }

    private void guardIsCommonKnowledge(String variable) {
        if (!composition.commonKnowledgeVars().contains(variable)) {
            throw new IllegalArgumentException(
                "Variable '" + variable + "' is not marked as common knowledge");
        }
    }
}

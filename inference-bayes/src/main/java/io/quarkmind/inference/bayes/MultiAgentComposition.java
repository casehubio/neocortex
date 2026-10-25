package io.quarkmind.inference.bayes;

import java.util.List;
import java.util.Set;

public record MultiAgentComposition(
    String name,
    NetworkComposition composition,
    List<String> agentNames,
    Set<String> commonKnowledgeVars
) {
    public MultiAgentComposition {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name must not be null or empty");
        }
        if (composition == null) {
            throw new IllegalArgumentException("Composition must not be null");
        }
        agentNames = List.copyOf(agentNames);
        commonKnowledgeVars = commonKnowledgeVars == null ? Set.of() : Set.copyOf(commonKnowledgeVars);
        for (String agent : agentNames) {
            composition.slotByName(agent);
        }
    }

    public int agentCount() {
        return agentNames.size();
    }

    public boolean hasAgent(String name) {
        return agentNames.contains(name);
    }
}

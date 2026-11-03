package io.casehub.neocortex.corpus.management;

public enum InteractionType {

    AGENT_HUMAN("agent-human"),
    AGENT_AGENT("agent-agent"),
    TOOL_USE("tool-use"),
    SELF_REFLECTION("self-reflection");

    private final String label;

    InteractionType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

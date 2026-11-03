package io.casehub.neocortex.corpus.management;

public enum ActivityType {

    RESEARCH("research"),
    IMPLEMENTATION("implementation"),
    DEBUGGING("debugging"),
    DESIGN_DISCUSSION("design-discussion"),
    REVIEW("review"),
    PLANNING("planning"),
    GENERAL("general");

    private final String label;

    ActivityType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

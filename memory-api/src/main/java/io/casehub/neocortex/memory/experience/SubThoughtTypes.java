package io.casehub.neocortex.memory.experience;

import java.util.Set;

public final class SubThoughtTypes {
    public static final String AFFECT_OBSERVATION = "affect-observation";
    public static final String CAUSAL_INFERENCE   = "causal-inference";
    public static final String EVALUATIVE         = "evaluative";
    public static final String INTENTION          = "intention";
    public static final String SELF_REFLECTION    = "self-reflection";
    public static final String ASSOCIATION        = "association";
    public static final String CONCERN            = "concern";

    private static final Set<String> KNOWN = Set.of(
        AFFECT_OBSERVATION, CAUSAL_INFERENCE, EVALUATIVE,
        INTENTION, SELF_REFLECTION, ASSOCIATION, CONCERN);

    public static boolean isKnown(String type) {
        return KNOWN.contains(type);
    }

    public static void validate(String type) {
        if (!isKnown(type)) {
            throw new IllegalArgumentException(
                "Unknown sub-thought type: " + type + ". Known: " + KNOWN);
        }
    }

    private SubThoughtTypes() {}
}

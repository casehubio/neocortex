package io.casehub.neocortex.memory.seeding.biography;

import java.util.Map;

public final class BiographyTemplateTypes {
    public static final String CULTURAL_CONTEXT     = "cultural-context";
    public static final String FORMATIVE_EXPERIENCE = "formative-experience";
    public static final String LIFE_EVENT           = "life-event";
    public static final String PLACE                = "place";
    public static final String ACTIVITY             = "activity";
    public static final String PROJECT              = "project";
    public static final String RELATIONSHIP         = "relationship";
    public static final String GOAL                 = "goal";
    public static final String BELIEF               = "belief";
    public static final String CURRENT_STATE        = "current-state";

    private static final Map<String, Integer> LAYER_MAP = Map.of(
        CULTURAL_CONTEXT, 1,
        FORMATIVE_EXPERIENCE, 2,
        LIFE_EVENT, 4,
        PLACE, 4,
        ACTIVITY, 4,
        PROJECT, 4,
        RELATIONSHIP, 5,
        GOAL, 6,
        BELIEF, 6,
        CURRENT_STATE, 8
    );

    public static int layerFor(String type) {
        Integer layer = LAYER_MAP.get(type);
        if (layer == null) throw new IllegalArgumentException("Unknown template type: " + type);
        return layer;
    }

    private BiographyTemplateTypes() {}
}

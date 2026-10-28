package io.casehub.neocortex.memory.seeding.biography;

import java.util.Map;

public final class BiographyTemplateTypes {
    public static final String CULTURAL_CONTEXT     = "cultural-context";
    public static final String FORMATIVE_EXPERIENCE = "formative-experience";
    public static final String LIFE_EVENT           = "life-event";
    public static final String PLACE                = "place";
    public static final String ACTIVITY             = "activity";
    public static final String PROJECT              = "project";
    public static final String ORGANISATION         = "organisation";
    public static final String RELATIONSHIP         = "relationship";
    public static final String GOAL                 = "goal";
    public static final String BELIEF               = "belief";
    public static final String ENTITY_ENRICHMENT    = "entity-enrichment";
    public static final String DISPOSITION_PROFILE   = "disposition-profile";
    public static final String PERSONALITY_PROFILE   = "personality-profile";
    public static final String CURRENT_STATE         = "current-state";

    private static final Map<String, Integer> LAYER_MAP = Map.ofEntries(
        Map.entry(CULTURAL_CONTEXT, 1),
        Map.entry(FORMATIVE_EXPERIENCE, 2),
        Map.entry(LIFE_EVENT, 4),
        Map.entry(PLACE, 4),
        Map.entry(ACTIVITY, 4),
        Map.entry(PROJECT, 4),
        Map.entry(ORGANISATION, 4),
        Map.entry(RELATIONSHIP, 5),
        Map.entry(GOAL, 6),
        Map.entry(BELIEF, 6),
        Map.entry(ENTITY_ENRICHMENT, 7),
        Map.entry(DISPOSITION_PROFILE, 7),
        Map.entry(PERSONALITY_PROFILE, 7),
        Map.entry(CURRENT_STATE, 8)
    );

    public static int layerFor(String type) {
        Integer layer = LAYER_MAP.get(type);
        if (layer == null) throw new IllegalArgumentException("Unknown template type: " + type);
        return layer;
    }

    private BiographyTemplateTypes() {}
}

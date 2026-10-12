package io.casehub.neocortex.memory.seeding.biography;

public final class EnrichmentAttributeKeys {

    private EnrichmentAttributeKeys() {}

    // OCC emotions (indexed)
    public static final String OCC_EMOTION_COUNT = "occ-emotion-count";
    public static String occEmotionType(int i) { return "occ-emotion-" + i + "-type"; }
    public static String occEmotionIntensity(int i) { return "occ-emotion-" + i + "-intensity"; }
    public static String occEmotionSource(int i) { return "occ-emotion-" + i + "-source"; }

    // SEC appraisal (flat)
    public static final String SEC_RELEVANCE = "sec-relevance";
    public static final String SEC_NOVELTY = "sec-novelty";
    public static final String SEC_URGENCY = "sec-urgency";
    public static final String SEC_CONDUCIVENESS = "sec-conduciveness";
    public static final String SEC_CONTROLLABILITY = "sec-controllability";
    public static final String SEC_ADJUSTABILITY = "sec-adjustability";
    public static final String SEC_INTERNAL_STANDARDS = "sec-internal-standards";
    public static final String SEC_EXTERNAL_STANDARDS = "sec-external-standards";

    // Action tendencies (indexed)
    public static final String ACTION_TENDENCY_COUNT = "action-tendency-count";
    public static String actionTendencyType(int i) { return "action-tendency-" + i + "-type"; }
    public static String actionTendencyIntensity(int i) { return "action-tendency-" + i + "-intensity"; }

    // Cognitive distortions (indexed)
    public static final String COGNITIVE_DISTORTION_COUNT = "cognitive-distortion-count";
    public static String cognitiveDistortionType(int i) { return "cognitive-distortion-" + i + "-type"; }
    public static String cognitiveDistortionStrength(int i) { return "cognitive-distortion-" + i + "-strength"; }

    // Drive impact (flat)
    public static final String DRIVE_CURIOSITY = "drive-curiosity";
    public static final String DRIVE_COMPETENCE = "drive-competence";
    public static final String DRIVE_AFFILIATION = "drive-affiliation";
    public static final String DRIVE_AUTONOMY = "drive-autonomy";

    // CAPS inputs (dynamic — caller must sanitize name to [a-z0-9_-])
    public static String capsInput(String name) { return "caps-" + name; }

    // Goal impacts (indexed)
    public static final String GOAL_IMPACT_COUNT = "goal-impact-count";
    public static String goalImpactGoal(int i) { return "goal-impact-" + i + "-goal"; }
    public static String goalImpactRelationship(int i) { return "goal-impact-" + i + "-relationship"; }
    public static String goalImpactUrgencyDelta(int i) { return "goal-impact-" + i + "-urgency-delta"; }

    // Habituation (flat)
    public static final String HABITUATION_NOVELTY = "habituation-novelty";
    public static final String HABITUATION_SCORE = "habituation-score";
    public static final String HABITUATION_ECHOES = "habituation-echoes";

    // Gut feeling (flat)
    public static final String GUT_VALENCE = "gut-valence";
    public static final String GUT_INTENSITY = "gut-intensity";
    public static final String GUT_RESONANCE = "gut-resonance";

    // Related entities (indexed)
    public static final String RELATED_ENTITY_COUNT = "related-entity-count";
    public static String relatedEntity(int i) { return "related-entity-" + i; }
}

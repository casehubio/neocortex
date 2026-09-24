package io.casehub.neocortex.cognitive;

public enum EmotionType {
    // Prospect-based (goal lifecycle)
    HOPE, FEAR, SATISFACTION, DISAPPOINTMENT, RELIEF, FEARS_CONFIRMED,
    // Well-being
    JOY, DISTRESS,
    // Fortunes of others
    HAPPY_FOR, PITY,
    // Attribution
    PRIDE, SHAME, REPROACH, ADMIRATION,
    // Compound
    GRATITUDE, ANGER, REMORSE, GRATIFICATION,
    // Object-based
    LOVE, HATE,
    // Fortunes-of-others (disliked)
    RESENTMENT, GLOATING
}

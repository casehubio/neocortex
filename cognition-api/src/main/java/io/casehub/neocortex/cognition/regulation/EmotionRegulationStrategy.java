package io.casehub.neocortex.cognition.regulation;

// Gross 1998 five-strategy process model — situation → attention → appraisal → response
public enum EmotionRegulationStrategy {
    SITUATION_SELECTION,       // Gross strategy 1 — avoid triggering contexts
    SITUATION_MODIFICATION,    // Gross strategy 2 — change the situation
    ATTENTIONAL_DEPLOYMENT,    // Gross strategy 3 — redirect focus (distraction)
    COGNITIVE_CHANGE,          // Gross strategy 4 — reappraise meaning
    RESPONSE_MODULATION,       // Gross strategy 5 — suppress/amplify expression
    // Zaki & Williams 2013 interpersonal strategies
    SOCIAL_SUPPORT_SEEKING,    // intrinsic — seek comfort from a specific agent
    SOCIAL_SHARING,            // intrinsic — share emotional experience with another
    SOCIAL_WITHDRAWAL,         // intrinsic — withdraw from social interaction
    CO_REGULATION,             // extrinsic — soothe/comfort a distressed agent
    DELIBERATE_PROVOCATION     // extrinsic — intentionally provoke emotional response
}

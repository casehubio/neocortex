package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.mentalmodel.MentalStateSignal;
import io.casehub.neocortex.cognition.mood.MoodSignal;
import io.casehub.neocortex.cognition.strategy.EngagementSignal;
import io.casehub.neocortex.cognition.usermodel.InteractionSignal;
import io.casehub.neocortex.memory.relationship.QualitySignal;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record CognitiveImpact(
        @Nullable InteractionSignal userModelSignal,
        @Nullable MoodSignal moodSignal,
        boolean suppressBdiExtraction,
        @Nullable List<MentalStateSignal> mentalStateSignals,
        @Nullable EngagementSignal strategySignal,
        @Nullable String conversationId) {

    public static CognitiveImpact fromText(String description) {
        return new CognitiveImpact(
                new InteractionSignal.CustomSignal(description, QualitySignal.NEUTRAL),
                null, false, null, null, null);
    }

    public static CognitiveImpact withConversationId(String conversationId) {
        return new CognitiveImpact(null, null, false, null, null, conversationId);
    }
}

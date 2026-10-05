package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import org.jspecify.annotations.Nullable;
import java.util.List;

public record AppraisalResult(
        List<CognitiveEmotion> emotions,
        List<ActionTendency> actionTendencies,
        HabituationState updatedHabituation,
        @Nullable String narrative) {

    public AppraisalResult {
        emotions = emotions != null ? List.copyOf(emotions) : List.of();
        actionTendencies = actionTendencies != null ? List.copyOf(actionTendencies) : List.of();
        if (updatedHabituation == null) updatedHabituation = HabituationState.empty();
    }

    public AppraisalResult(List<CognitiveEmotion> emotions,
                           List<ActionTendency> actionTendencies,
                           HabituationState updatedHabituation) {
        this(emotions, actionTendencies, updatedHabituation, null);
    }

    public static AppraisalResult empty() {
        return new AppraisalResult(List.of(), List.of(), HabituationState.empty(), null);
    }
}

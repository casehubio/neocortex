package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import java.util.List;
import java.util.Objects;

public record AppraisalContext(
        PerceivedSituation situation,
        List<Drive> drives,
        AppraisalWeights weights,
        HabituationConfig habituationConfig,
        HabituationState habituation,
        MoodState currentMood) {
    public AppraisalContext {
        Objects.requireNonNull(situation, "situation");
        drives = drives != null ? List.copyOf(drives) : List.of();
    }
}

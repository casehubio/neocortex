package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record AppraisalContext(
        PerceivedSituation situation,
        List<Drive> drives,
        AppraisalWeights weights,
        HabituationConfig habituationConfig,
        HabituationState habituation,
        MoodState currentMood,
        @Nullable DispositionAxes dispositionAxes) {

    public AppraisalContext(
            PerceivedSituation situation,
            List<Drive> drives,
            AppraisalWeights weights,
            HabituationConfig habituationConfig,
            HabituationState habituation,
            MoodState currentMood) {
        this(situation, drives, weights, habituationConfig, habituation, currentMood, null);
    }

    public AppraisalContext {
        Objects.requireNonNull(situation, "situation");
        drives = drives != null ? List.copyOf(drives) : List.of();
    }
}

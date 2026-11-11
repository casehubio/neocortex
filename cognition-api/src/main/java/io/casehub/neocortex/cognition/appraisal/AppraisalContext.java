package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.cognitive.index.PersonalityFacet;
import io.casehub.neocortex.cognitive.index.RelationalSchema;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record AppraisalContext(
        String agentId,
        PerceivedSituation situation,
        List<Drive> drives,
        AppraisalWeights weights,
        HabituationConfig habituationConfig,
        HabituationState habituation,
        MoodState currentMood,
        @Nullable DispositionAxes dispositionAxes,
        @Nullable RelationalSchema targetSchema,
        @Nullable List<PersonalityFacet> personalityFacets) {

    public AppraisalContext(
            String agentId,
            PerceivedSituation situation,
            List<Drive> drives,
            AppraisalWeights weights,
            HabituationConfig habituationConfig,
            HabituationState habituation,
            MoodState currentMood,
            @Nullable DispositionAxes dispositionAxes) {
        this(agentId, situation, drives, weights, habituationConfig, habituation, currentMood,
             dispositionAxes, null, null);
    }

    public AppraisalContext(
            String agentId,
            PerceivedSituation situation,
            List<Drive> drives,
            AppraisalWeights weights,
            HabituationConfig habituationConfig,
            HabituationState habituation,
            MoodState currentMood) {
        this(agentId, situation, drives, weights, habituationConfig, habituation, currentMood,
             null, null, null);
    }

    public AppraisalContext {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(situation, "situation");
        drives = drives != null ? List.copyOf(drives) : List.of();
    }
}

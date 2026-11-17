package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.mood.MoodBaseline;
import io.casehub.neocortex.mindmap.AppraisalWeights;

public record EmotionDefaults(
        MoodBaseline moodBaseline,
        AppraisalWeights appraisalWeights,
        RegulationDefaults regulationDefaults,
        CopingDefaults copingDefaults
) {
    public static EmotionDefaults empty() {
        return new EmotionDefaults(null, null, null, null);
    }

    public EmotionDefaults mergeWith(EmotionDefaults derived) {
        if (derived == null) return this;
        return new EmotionDefaults(
                moodBaseline != null ? moodBaseline : derived.moodBaseline(),
                appraisalWeights != null ? appraisalWeights : derived.appraisalWeights(),
                regulationDefaults != null ? regulationDefaults : derived.regulationDefaults(),
                copingDefaults != null ? copingDefaults : derived.copingDefaults());
    }
}

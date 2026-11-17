package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.mindmap.CuriosityConfig;

public record BehavioralDefaults(
        CuriosityConfig curiosity,
        TemporalFocusConfig temporalFocus,
        SocialCognitionDefaults socialCognition,
        CbrStrategyDefaults cbrStrategy
) {
    public static BehavioralDefaults empty() {
        return new BehavioralDefaults(null, null, null, null);
    }

    public BehavioralDefaults mergeWith(BehavioralDefaults derived) {
        if (derived == null) return this;
        return new BehavioralDefaults(
                curiosity != null ? curiosity : derived.curiosity(),
                temporalFocus != null ? temporalFocus : derived.temporalFocus(),
                socialCognition != null ? socialCognition : derived.socialCognition(),
                cbrStrategy != null ? cbrStrategy : derived.cbrStrategy());
    }
}

package io.casehub.neocortex.mindmap;

public final class ActivityVocabulary {

    public static final MindMapVocabulary ACTIVITY_VOCABULARY = MindMapVocabulary.builder()
            .edgeType("participated", "took-part-in", "attended")
            .edgeType("at", "held-at", "located-at")
            .edgeType("occasion", "linked-event", "calendar-event")
            .build();

    private ActivityVocabulary() {}
}

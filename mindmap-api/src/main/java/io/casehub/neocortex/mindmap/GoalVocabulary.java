package io.casehub.neocortex.mindmap;

public final class GoalVocabulary {

    public static final MindMapVocabulary GOAL_VOCABULARY = MindMapVocabulary.builder()
            .edgeType("enables", "makes-possible", "unblocks")
            .edgeType("blocks", "prevents", "gates")
            .edgeType("requires", "depends-on", "needs")
            .edgeType("contributes-to", "supports", "helps")
            .edgeType("decomposes-into", "breaks-down-to", "involves")
            .build();

    private GoalVocabulary() {}
}

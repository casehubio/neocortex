package io.casehub.neocortex.mindmap;

public final class GoalVocabulary {

    public static final MindMapVocabulary GOAL_VOCABULARY = MindMapVocabulary.builder()
            .edgeType("enables", "makes-possible", "unblocks")
            .edgeType("blocks", "prevents", "gates")
            .edgeType("requires", "depends-on", "needs")
            .edgeType("contributes-to", "supports", "helps")
            .edgeType("decomposes-into", "breaks-down-to", "involves")
            .edgeType("aspires-to", "pursues", "strives-for")
            .build();
    public static final java.util.Set<String> CONTENT_DOMAINS = java.util.Set.of(
            "morality-and-virtue", "religion-and-spirituality", "self-actualisation",
            "avoidance", "social-relating", "health", "family",
            "mastery-and-competence", "financial-and-occupational");

    public static final java.util.Set<String> MOTIVATION_ORIGINS = java.util.Set.of(
            "intrinsic", "identified", "introjected", "external");

    public static final java.util.Set<String> CONTENT_SUBDOMAINS = java.util.Set.of(
            "social-values", "personal-morals", "help-others", "highly-regarded",
            "religion-and-spirituality",
            "wisdom-and-serenity", "self-knowledge-and-happiness", "appreciating-beauty",
            "exploration-and-passions", "enjoy-life",
            "avoid-stress-harm-rejection-conflict", "avoid-hassles",
            "relate-and-belong", "intimacy", "stability", "power", "leadership",
            "health", "family",
            "achievement", "self-efficacy", "practical-competence", "intellectual-competence",
            "finances", "occupational-success");


    private GoalVocabulary() {}
}

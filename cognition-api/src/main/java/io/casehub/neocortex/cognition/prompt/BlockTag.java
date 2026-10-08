package io.casehub.neocortex.cognition.prompt;

public enum BlockTag {

    // Tier 1 — Identity (who you are)
    BEHAVIORAL(100),
    PERSONALITY(110),
    CONSTRAINTS(120),

    // Tier 2 — State (how you feel right now)
    MOOD(200),
    DRIVES(210),
    APPRAISAL(220),
    NEEDS(230),

    // Tier 3 — Knowledge (what you know)
    BELIEFS(300),
    MENTAL_MODEL(310),
    NARRATIVE(320),
    SOCIAL(330),
    FORMATION(340),

    // Tier 4 — Strategy (what you're trying to do)
    GOALS(400),
    STRATEGY(410),
    ATTENTION(420),
    TEMPORAL(430),

    // Tier 5 — Signals (system notifications)
    MEMORY(500),
    REFLECTION(510),
    CONSOLIDATION(520),
    QUEUE(530),

    // Tier 6 — Stimulus (always last)
    CHAT(900);

    private final int ordinal;

    BlockTag(int ordinal) {
        this.ordinal = ordinal;
    }

    public int sortOrdinal() {
        return ordinal;
    }

    public String render(String content) {
        return "[" + name() + "] " + content;
    }
}

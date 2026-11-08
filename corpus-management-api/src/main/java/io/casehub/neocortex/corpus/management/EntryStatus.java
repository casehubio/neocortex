package io.casehub.neocortex.corpus.management;

public enum EntryStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public static EntryStatus fromString(String value) {
        if (value == null || value.isBlank()) return PUBLISHED;
        return switch (value.toLowerCase()) {
            case "draft" -> DRAFT;
            case "archived" -> ARCHIVED;
            default -> PUBLISHED;
        };
    }
}

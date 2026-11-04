package io.casehub.neocortex.mindmap;

public final class PersonaVocabulary {

    public static final MindMapVocabulary PERSONA_VOCABULARY = MindMapVocabulary.builder()
            .edgeType("persona-of", "alter-ego", "stage-persona")
            .build();

    private PersonaVocabulary() {}
}

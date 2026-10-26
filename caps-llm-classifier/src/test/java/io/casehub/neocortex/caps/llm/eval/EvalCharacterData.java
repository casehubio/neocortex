package io.casehub.neocortex.caps.llm.eval;

import java.util.List;

record EvalCharacterData(
        List<Drive> drives,
        List<FormationMemory> formationMemories,
        List<String> tendencies,
        List<NormEntry> norms,
        List<InitialBelief> initialBeliefs,
        List<PersonalityFacet> personalityFacets,
        List<RelationalSchema> relationalSchemas,
        AttachmentStyle attachment,
        String substrate
) {
    EvalCharacterData {
        drives = drives != null ? List.copyOf(drives) : List.of();
        formationMemories = formationMemories != null ? List.copyOf(formationMemories) : List.of();
        tendencies = tendencies != null ? List.copyOf(tendencies) : List.of();
        norms = norms != null ? List.copyOf(norms) : List.of();
        initialBeliefs = initialBeliefs != null ? List.copyOf(initialBeliefs) : List.of();
        personalityFacets = personalityFacets != null ? List.copyOf(personalityFacets) : List.of();
        relationalSchemas = relationalSchemas != null ? List.copyOf(relationalSchemas) : List.of();
    }

    static EvalCharacterData empty() {
        return new EvalCharacterData(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, null);
    }

    record Drive(String type, double intensity, String description, String context) {
        Drive {
            if (description == null) description = "";
            if (context == null) context = "any";
        }
    }

    record FormationMemory(int age, String episode, double pleasure, double arousal, double dominance,
                           List<DriveAnnotation> annotations) {
        FormationMemory {
            annotations = annotations != null ? List.copyOf(annotations) : List.of();
        }

        FormationMemory(int age, String episode, double pleasure, double arousal, double dominance) {
            this(age, episode, pleasure, arousal, dominance, List.of());
        }
    }

    record DriveAnnotation(String drive, String degree, String text, int startOffset, int endOffset) {}

    record NormEntry(String rule, int priority) {}

    record InitialBelief(String key, String value) {}

    record PersonalityFacet(String facet, int score, String origin) {}

    record RelationalSchema(String target, String role, double trust, double intimacy, double utility,
                            String description) {}

    record AttachmentStyle(String style, double anxiety, double avoidance) {}
}

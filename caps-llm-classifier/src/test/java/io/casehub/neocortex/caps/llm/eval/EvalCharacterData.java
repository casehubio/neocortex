package io.casehub.neocortex.caps.llm.eval;

import java.util.List;

record EvalCharacterData(
        List<Drive> drives,
        List<FormationMemory> formationMemories
) {
    EvalCharacterData {
        drives = drives != null ? List.copyOf(drives) : List.of();
        formationMemories = formationMemories != null ? List.copyOf(formationMemories) : List.of();
    }

    static EvalCharacterData empty() {
        return new EvalCharacterData(List.of(), List.of());
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
}

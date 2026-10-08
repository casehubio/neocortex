package io.casehub.neocortex.memory.experience;

import io.casehub.neocortex.memory.Memory;

public final class FormativeScoring {

    private FormativeScoring() {}

    public static boolean isFormative(Memory memory) {
        return "formative".equals(
                memory.attributes().getOrDefault(ExperienceAttributeKeys.EVENT_TYPE, ""));
    }

    public static double score(Memory memory) {
        double base = memory.confidence() != null ? memory.confidence().value() : 0.8;
        String salienceStr = memory.attributes().get(FormativeAttributeKeys.SALIENCE_MULTIPLIER);
        double salience = salienceStr != null ? Double.parseDouble(salienceStr) : 1.0;
        return Math.min(1.0, base * salience);
    }
}

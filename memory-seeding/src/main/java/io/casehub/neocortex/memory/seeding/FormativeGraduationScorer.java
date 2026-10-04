package io.casehub.neocortex.memory.seeding;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.GraduationContext;
import io.casehub.neocortex.memory.experience.GraduationScorer;

public class FormativeGraduationScorer implements GraduationScorer {

    private final GraduationScorer delegate;

    public FormativeGraduationScorer(GraduationScorer delegate) {
        this.delegate = delegate;
    }

    @Override
    public double score(Memory memory, GraduationContext context) {
        String eventType = memory.attributes()
            .getOrDefault(ExperienceAttributeKeys.EVENT_TYPE, "");
        if ("formative".equals(eventType)) {
            return memory.confidence() != null
                ? memory.confidence().value() : 0.8;
        }
        return delegate.score(memory, context);
    }
}

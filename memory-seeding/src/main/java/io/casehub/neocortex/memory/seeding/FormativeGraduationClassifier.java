package io.casehub.neocortex.memory.seeding;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.FormativeAttributeKeys;
import io.casehub.neocortex.memory.experience.GraduationClassifier;
import io.casehub.neocortex.memory.experience.GraduationResult;

import java.util.HashMap;

public class FormativeGraduationClassifier implements GraduationClassifier {

    private final GraduationClassifier delegate;

    public FormativeGraduationClassifier(GraduationClassifier delegate) {
        this.delegate = delegate;
    }

    @Override
    public GraduationResult classify(Memory memory) {
        String eventType = memory.attributes()
            .getOrDefault(ExperienceAttributeKeys.EVENT_TYPE, "");
        if ("formative".equals(eventType)) {
            var props = new HashMap<String, String>();
            propagateIfPresent(memory, props, FormativeAttributeKeys.CATALOGUE_ENTRY_ID);
            propagateIfPresent(memory, props, FormativeAttributeKeys.SITUATION_TYPES);
            propagateIfPresent(memory, props, FormativeAttributeKeys.SALIENCE_MULTIPLIER);
            propagateIfPresent(memory, props, FormativeAttributeKeys.DEVELOPMENTAL_PERIOD);
            propagateIfPresent(memory, props, FormativeAttributeKeys.REINFORCEMENT_SCHEDULE);
            return new GraduationResult("formative-experience", ConfidenceOrigin.STATED, props);
        }
        return delegate.classify(memory);
    }

    private static void propagateIfPresent(Memory memory, HashMap<String, String> props, String key) {
        String value = memory.attributes().get(key);
        if (value != null) props.put(key, value);
    }
}

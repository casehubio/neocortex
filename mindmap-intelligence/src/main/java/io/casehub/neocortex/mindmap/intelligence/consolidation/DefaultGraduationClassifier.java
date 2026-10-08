package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.FormativeAttributeKeys;
import io.casehub.neocortex.memory.experience.GraduationClassifier;
import io.casehub.neocortex.memory.experience.GraduationResult;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;

@DefaultBean
@ApplicationScoped
public class DefaultGraduationClassifier implements GraduationClassifier {

    @Override
    public GraduationResult classify(Memory memory) {
        String eventType = memory.attributes().getOrDefault(
            ExperienceAttributeKeys.EVENT_TYPE, "observation");

        return switch (eventType) {
            case "observation" -> classifyObservation(memory);
            case "action"      -> classifyAction(memory);
            case "outcome"     -> classifyOutcome(memory);
            case "formative"   -> classifyFormative(memory);
            default            -> new GraduationResult("belief",
                                      ConfidenceOrigin.INFERRED, Map.of());
        };
    }

    private GraduationResult classifyObservation(Memory memory) {
        String              subject = memory.attributes().get(ExperienceAttributeKeys.SUBJECT);
        Map<String, String> props   = new HashMap<>();
        if (subject != null) {props.put("subject", subject);}
        props.put("status", "active");
        String emotionType = memory.attributes().get("emotion-type");
        if (emotionType != null) {
            props.put("emotion-type", emotionType);
            String emotionIntensity = memory.attributes().get("emotion-intensity");
            if (emotionIntensity != null) {props.put("emotion-intensity", emotionIntensity);}
        }
        return new GraduationResult("belief", ConfidenceOrigin.STATED, props);
    }

    private GraduationResult classifyAction(Memory memory) {
        String capability = memory.attributes().get(ExperienceAttributeKeys.CAPABILITY);
        Map<String, String> props = new HashMap<>();
        if (capability != null) props.put("goal", capability);
        props.put("status", "active");
        return new GraduationResult("intention", ConfidenceOrigin.INFERRED, props);
    }

    private GraduationResult classifyOutcome(Memory memory) {
        String result = memory.attributes().get(ExperienceAttributeKeys.RESULT);
        Map<String, String> props = new HashMap<>();
        if (result != null) props.put("target", result);
        return new GraduationResult("judgment", ConfidenceOrigin.INFERRED, props);
    }

    private GraduationResult classifyFormative(Memory memory) {
        var props = new HashMap<String, String>();
        propagateIfPresent(memory, props, FormativeAttributeKeys.CATALOGUE_ENTRY_ID);
        propagateIfPresent(memory, props, FormativeAttributeKeys.SITUATION_TYPES);
        propagateIfPresent(memory, props, FormativeAttributeKeys.SALIENCE_MULTIPLIER);
        propagateIfPresent(memory, props, FormativeAttributeKeys.DEVELOPMENTAL_PERIOD);
        propagateIfPresent(memory, props, FormativeAttributeKeys.REINFORCEMENT_SCHEDULE);
        return new GraduationResult("formative-experience", ConfidenceOrigin.STATED, props);
    }

    private static void propagateIfPresent(Memory memory, Map<String, String> props, String key) {
        String value = memory.attributes().get(key);
        if (value != null) props.put(key, value);
    }
}

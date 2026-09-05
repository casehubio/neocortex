package io.casehub.neocortex.memory.experience;

import io.casehub.neocortex.cognitive.Confidence;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;

import java.util.HashMap;
import java.util.HashSet;

public final class ExperienceEvents {

    public static final MemoryDomain DOMAIN = new MemoryDomain("experience");

    private ExperienceEvents() {}

    public static MemoryInput toMemoryInput(ExperienceEvent event) {
        var reserved = new HashSet<String>();
        var attrs = new HashMap<String, String>();

        reserved.add(ExperienceAttributeKeys.EVENT_TYPE);
        attrs.put(ExperienceAttributeKeys.EVENT_TYPE, eventTypeName(event));

        if (event.turnId() != null) {
            reserved.add(ExperienceAttributeKeys.TURN_ID);
            attrs.put(ExperienceAttributeKeys.TURN_ID, event.turnId());
        }

        reserved.add(ExperienceAttributeKeys.TIMESTAMP);
        attrs.put(ExperienceAttributeKeys.TIMESTAMP, event.timestamp().toString());

        switch (event) {
            case Observation o -> {
                reserved.add(ExperienceAttributeKeys.SUBJECT);
                attrs.put(ExperienceAttributeKeys.SUBJECT, o.subject());
            }
            case Action a -> {
                if (a.capability() != null) {
                    reserved.add(ExperienceAttributeKeys.CAPABILITY);
                    attrs.put(ExperienceAttributeKeys.CAPABILITY, a.capability());
                }
            }
            case Outcome o -> {
                reserved.add(ExperienceAttributeKeys.RESULT);
                attrs.put(ExperienceAttributeKeys.RESULT, o.result());
                if (o.capability() != null) {
                    reserved.add(ExperienceAttributeKeys.CAPABILITY);
                    attrs.put(ExperienceAttributeKeys.CAPABILITY, o.capability());
                }
            }
        }

        for (String key : event.metadata().keySet()) {
            if (reserved.contains(key)) {
                throw new IllegalArgumentException(
                    "metadata key '" + key + "' collides with a reserved experience attribute key");
            }
        }

        attrs.putAll(event.metadata());

        return new MemoryInput(Subject.of("agent", event.agentId()), DOMAIN, event.tenantId(),
                               event.caseId(), event.description(), attrs, event.confidence() != null ? Confidence.unknown(event.confidence()) : null, null, null, null, event.agentId(), null);
    }

    private static String eventTypeName(ExperienceEvent event) {
        return switch (event) {
            case Observation o -> "observation";
            case Action a      -> "action";
            case Outcome o     -> "outcome";
        };
    }
}

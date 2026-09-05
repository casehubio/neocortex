package io.casehub.neocortex.memory.engagement;

import io.casehub.neocortex.cognitive.Confidence;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import java.util.HashMap;
import java.util.HashSet;

public final class EngagementEvents {

    public static final MemoryDomain DOMAIN = new MemoryDomain("engagement");

    private EngagementEvents() {}

    public static MemoryInput toMemoryInput(EngagementEvent event) {
        var reserved = new HashSet<String>();
        var attrs = new HashMap<String, String>();

        reserved.add(EngagementAttributeKeys.OTHER_AGENT);
        attrs.put(EngagementAttributeKeys.OTHER_AGENT, event.otherAgentId());

        reserved.add(EngagementAttributeKeys.TURN_ID);
        attrs.put(EngagementAttributeKeys.TURN_ID, event.turnId());

        reserved.add(EngagementAttributeKeys.TIMESTAMP);
        attrs.put(EngagementAttributeKeys.TIMESTAMP, event.timestamp().toString());

        addIfPresent(reserved, attrs, EngagementAttributeKeys.RESPONDED, event.responded());
        addIfPresent(reserved, attrs, EngagementAttributeKeys.RESPONSE_TIME_MS, event.responseTimeMs());
        addIfPresent(reserved, attrs, EngagementAttributeKeys.RESPONSE_LENGTH, event.responseLength());
        addIfPresent(reserved, attrs, EngagementAttributeKeys.AFFECT_SHIFT, event.affectShift());
        addIfPresent(reserved, attrs, EngagementAttributeKeys.REACTION_COUNT, event.reactionCount());
        addIfPresent(reserved, attrs, EngagementAttributeKeys.CONTINUED, event.continued());

        for (String key : event.metadata().keySet()) {
            if (reserved.contains(key)) {
                throw new IllegalArgumentException(
                    "metadata key '" + key + "' collides with a reserved engagement attribute key");
            }
        }

        attrs.putAll(event.metadata());

        return new MemoryInput(Subject.of("agent", event.agentId()), DOMAIN, event.tenantId(),
                               event.caseId(), event.description(), attrs, event.confidence() != null ? Confidence.unknown(event.confidence()) : null, null, null, null, event.agentId(), null);
    }

    private static void addIfPresent(HashSet<String> reserved, HashMap<String, String> attrs,
            String key, Object value) {
        if (value != null) {
            reserved.add(key);
            attrs.put(key, String.valueOf(value));
        }
    }
}

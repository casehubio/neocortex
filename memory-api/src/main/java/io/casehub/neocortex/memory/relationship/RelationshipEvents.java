package io.casehub.neocortex.memory.relationship;

import io.casehub.neocortex.cognitive.Confidence;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

public final class RelationshipEvents {

    public static final MemoryDomain DOMAIN = new MemoryDomain("relationship");

    private RelationshipEvents() {}

    public static MemoryInput toMemoryInput(RelationshipEvent event) {
        var reserved = new HashSet<String>();
        var attrs = new HashMap<String, String>();

        reserved.add(RelationshipAttributeKeys.OTHER_AGENT);
        attrs.put(RelationshipAttributeKeys.OTHER_AGENT, event.otherAgentId());

        reserved.add(RelationshipAttributeKeys.SOURCE_EVENT_TYPE);
        attrs.put(RelationshipAttributeKeys.SOURCE_EVENT_TYPE, event.sourceEventType());

        reserved.add(RelationshipAttributeKeys.QUALITY_SIGNAL);
        attrs.put(RelationshipAttributeKeys.QUALITY_SIGNAL,
            event.qualitySignal().name().toLowerCase(Locale.ROOT));

        if (event.turnId() != null) {
            reserved.add(RelationshipAttributeKeys.TURN_ID);
            attrs.put(RelationshipAttributeKeys.TURN_ID, event.turnId());
        }

        reserved.add(RelationshipAttributeKeys.TIMESTAMP);
        attrs.put(RelationshipAttributeKeys.TIMESTAMP, event.timestamp().toString());

        for (String key : event.metadata().keySet()) {
            if (reserved.contains(key)) {
                throw new IllegalArgumentException(
                    "metadata key '" + key + "' collides with a reserved relationship attribute key");
            }
        }

        attrs.putAll(event.metadata());

        return new MemoryInput(event.agentId(), DOMAIN, event.tenantId(),
            event.caseId(), event.description(), attrs, event.confidence() != null ? Confidence.unknown(event.confidence()) : null);
    }
}

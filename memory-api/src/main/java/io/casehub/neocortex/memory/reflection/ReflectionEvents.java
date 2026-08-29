package io.casehub.neocortex.memory.reflection;

import io.casehub.neocortex.cognitive.Confidence;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import java.util.HashMap;
import java.util.HashSet;

public final class ReflectionEvents {

    public static final MemoryDomain DOMAIN = new MemoryDomain("reflection");

    private ReflectionEvents() {}

    public static MemoryInput toMemoryInput(ReflectionEvent event) {
        var reserved = new HashSet<String>();
        var attrs = new HashMap<String, String>();

        reserved.add(ReflectionAttributeKeys.LEVEL);
        attrs.put(ReflectionAttributeKeys.LEVEL, String.valueOf(event.level()));

        reserved.add(ReflectionAttributeKeys.SOURCE_MEMORY_IDS);
        attrs.put(ReflectionAttributeKeys.SOURCE_MEMORY_IDS,
            String.join(",", event.sourceMemoryIds()));

        for (String key : event.metadata().keySet()) {
            if (reserved.contains(key)) {
                throw new IllegalArgumentException(
                    "metadata key '" + key + "' collides with a reserved reflection attribute key");
            }
        }

        attrs.putAll(event.metadata());

        double confidenceValue = event.confidence() != null
            ? event.confidence()
            : Math.min(0.3 + (event.level() * 0.2), 1.0);

        return new MemoryInput(event.agentId(), DOMAIN, event.tenantId(),
            event.caseId(), event.insight(), attrs, Confidence.unknown(confidenceValue));
    }
}

package io.casehub.neocortex.memory.mood;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import java.util.HashMap;
import java.util.HashSet;

public final class MoodEvents {

    public static final MemoryDomain DOMAIN = new MemoryDomain("mood");

    private MoodEvents() {}

    public static MemoryInput toMemoryInput(MoodState state) {
        var reserved = new HashSet<String>();
        var attrs = new HashMap<String, String>();

        reserved.add(MoodAttributeKeys.PLEASURE);
        attrs.put(MoodAttributeKeys.PLEASURE, String.valueOf(state.pleasure()));

        reserved.add(MoodAttributeKeys.AROUSAL);
        attrs.put(MoodAttributeKeys.AROUSAL, String.valueOf(state.arousal()));

        reserved.add(MoodAttributeKeys.DOMINANCE);
        attrs.put(MoodAttributeKeys.DOMINANCE, String.valueOf(state.dominance()));

        if (state.turnId() != null) {
            reserved.add(MoodAttributeKeys.TURN_ID);
            attrs.put(MoodAttributeKeys.TURN_ID, state.turnId());
        }

        reserved.add(MoodAttributeKeys.TIMESTAMP);
        attrs.put(MoodAttributeKeys.TIMESTAMP, state.timestamp().toString());

        for (String key : state.metadata().keySet()) {
            if (reserved.contains(key)) {
                throw new IllegalArgumentException(
                    "metadata key '" + key + "' collides with a reserved mood attribute key");
            }
        }

        attrs.putAll(state.metadata());

        return new MemoryInput(Subject.of("agent", state.agentId()), DOMAIN, state.tenantId(),
                               null, state.cause(), attrs, null,
                               state.pleasure(), state.arousal(), state.dominance(), state.agentId(), null);
    }
}

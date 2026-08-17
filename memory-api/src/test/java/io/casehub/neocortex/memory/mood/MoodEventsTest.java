package io.casehub.neocortex.memory.mood;

import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MoodEventsTest {

    @Test
    void domainIsMood() {
        assertEquals(new MemoryDomain("mood"), MoodEvents.DOMAIN);
    }

    @Test
    void convertsToMemoryInput() {
        var state = new MoodState("a1", "t1", 0.7, -0.3, 0.5, "good news",
            "turn-1", Map.of("extra", "val"));
        var input = MoodEvents.toMemoryInput(state);

        assertEquals("a1", input.entityId());
        assertEquals(MoodEvents.DOMAIN, input.domain());
        assertEquals("t1", input.tenantId());
        assertEquals("good news", input.text());
        assertEquals("0.7", input.attributes().get(MoodAttributeKeys.PLEASURE));
        assertEquals("-0.3", input.attributes().get(MoodAttributeKeys.AROUSAL));
        assertEquals("0.5", input.attributes().get(MoodAttributeKeys.DOMINANCE));
        assertEquals("turn-1", input.attributes().get(MoodAttributeKeys.TURN_ID));
        assertEquals("val", input.attributes().get("extra"));
    }

    @Test
    void omitsTurnIdWhenNull() {
        var state = new MoodState("a1", "t1", 0.0, 0.0, 0.0, "init", null, Map.of());
        var input = MoodEvents.toMemoryInput(state);
        assertFalse(input.attributes().containsKey(MoodAttributeKeys.TURN_ID));
    }

    @Test
    void rejectsMetadataCollidingWithReservedKeys() {
        var state = new MoodState("a1", "t1", 0.0, 0.0, 0.0, "init", null,
            Map.of(MoodAttributeKeys.PLEASURE, "hijack"));
        assertThrows(IllegalArgumentException.class, () -> MoodEvents.toMemoryInput(state));
    }
}

package io.casehub.neocortex.memory.mood;

import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MoodEventsTest {

    @Test
    void domainIsMood() {
        assertEquals(new MemoryDomain("mood"), MoodEvents.DOMAIN);
    }

    @Test
    void convertsToMemoryInput() {
        var state = new MoodState("a1", "t1", null, 0.7, -0.3, 0.5, "good news",
                                  "turn-1", null, Map.of("extra", "val"));
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
        var state = new MoodState("a1", "t1", null, 0.0, 0.0, 0.0, "init", null, null, Map.of());
        var input = MoodEvents.toMemoryInput(state);
        assertFalse(input.attributes().containsKey(MoodAttributeKeys.TURN_ID));
    }

    @Test
    void rejectsMetadataCollidingWithReservedKeys() {
        var state = new MoodState("a1", "t1", null, 0.0, 0.0, 0.0, "init", null, null,
                                  Map.of(MoodAttributeKeys.PLEASURE, "hijack"));
        assertThrows(IllegalArgumentException.class, () -> MoodEvents.toMemoryInput(state));
    }

    @Test
    void storesActiveContextIdsWhenPresent() {
        var state = new MoodState("a1", "t1", null, 0.5, 0.0, 0.0,
                                  "test", null, Set.of("sg-work", "sg-family"), Map.of());
        var input = MoodEvents.toMemoryInput(state);
        String value = input.attributes().get(MoodAttributeKeys.ACTIVE_CONTEXT_IDS);
        assertNotNull(value);
        assertTrue(value.contains("sg-work"));
        assertTrue(value.contains("sg-family"));
    }

    @Test
    void omitsActiveContextIdsWhenNull() {
        var state = new MoodState("a1", "t1", null, 0.5, 0.0, 0.0,
                                  "test", null, null, Map.of());
        var input = MoodEvents.toMemoryInput(state);
        assertFalse(input.attributes().containsKey(MoodAttributeKeys.ACTIVE_CONTEXT_IDS));
    }

    @Test
    void omitsActiveContextIdsWhenEmpty() {
        var state = new MoodState("a1", "t1", null, 0.5, 0.0, 0.0,
                                  "test", null, Set.of(), Map.of());
        var input = MoodEvents.toMemoryInput(state);
        assertFalse(input.attributes().containsKey(MoodAttributeKeys.ACTIVE_CONTEXT_IDS));
    }
}

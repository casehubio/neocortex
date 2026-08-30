package io.casehub.neocortex.memory.engagement;

import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EngagementEventsTest {

    @Test
    void domainIsEngagement() {
        assertEquals(new MemoryDomain("engagement"), EngagementEvents.DOMAIN);
    }

    @Test
    void convertsFullEventToMemoryInput() {
        var event = new EngagementEvent("a1", "b1", "t1", "c1", "turn-1",
                                        null, "user responded", 0.7, Map.of("extra", "val"),
                                        true, 1500L, 142, 0.3, 2, true);
        var input = EngagementEvents.toMemoryInput(event);

        assertEquals("a1", input.entityId());
        assertEquals(EngagementEvents.DOMAIN, input.domain());
        assertEquals("t1", input.tenantId());
        assertEquals("c1", input.caseId());
        assertEquals("user responded", input.text());
        assertEquals(0.7, input.confidence().value());
        assertEquals("b1", input.attributes().get(EngagementAttributeKeys.OTHER_AGENT));
        assertEquals("turn-1", input.attributes().get(EngagementAttributeKeys.TURN_ID));
        assertEquals("true", input.attributes().get(EngagementAttributeKeys.RESPONDED));
        assertEquals("1500", input.attributes().get(EngagementAttributeKeys.RESPONSE_TIME_MS));
        assertEquals("142", input.attributes().get(EngagementAttributeKeys.RESPONSE_LENGTH));
        assertEquals("0.3", input.attributes().get(EngagementAttributeKeys.AFFECT_SHIFT));
        assertEquals("2", input.attributes().get(EngagementAttributeKeys.REACTION_COUNT));
        assertEquals("true", input.attributes().get(EngagementAttributeKeys.CONTINUED));
        assertEquals("val", input.attributes().get("extra"));
    }

    @Test
    void omitsNullSignals() {
        var event = new EngagementEvent("a1", "b1", "t1", null, "turn-1",
                                        null, "no response", null, Map.of(), null, null, null, null, null, null);
        var input = EngagementEvents.toMemoryInput(event);

        assertTrue(input.attributes().containsKey(EngagementAttributeKeys.OTHER_AGENT));
        assertTrue(input.attributes().containsKey(EngagementAttributeKeys.TURN_ID));
        assertFalse(input.attributes().containsKey(EngagementAttributeKeys.RESPONDED));
        assertFalse(input.attributes().containsKey(EngagementAttributeKeys.RESPONSE_TIME_MS));
        assertFalse(input.attributes().containsKey(EngagementAttributeKeys.RESPONSE_LENGTH));
        assertFalse(input.attributes().containsKey(EngagementAttributeKeys.AFFECT_SHIFT));
        assertFalse(input.attributes().containsKey(EngagementAttributeKeys.REACTION_COUNT));
        assertFalse(input.attributes().containsKey(EngagementAttributeKeys.CONTINUED));
    }

    @Test
    void rejectsMetadataCollidingWithReservedKeys() {
        var event = new EngagementEvent("a1", "b1", "t1", null, "turn-1",
                                        null, "desc", null, Map.of(EngagementAttributeKeys.OTHER_AGENT, "hijack"),
                                        null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> EngagementEvents.toMemoryInput(event));
    }
}

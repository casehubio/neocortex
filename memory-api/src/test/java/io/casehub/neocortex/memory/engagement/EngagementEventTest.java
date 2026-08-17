package io.casehub.neocortex.memory.engagement;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EngagementEventTest {

    @Test
    void requiresAgentId() {
        assertThrows(NullPointerException.class, () ->
            new EngagementEvent(null, "b1", "t1", null, "turn-1", "desc",
                null, Map.of(), true, null, null, null, null, null));
    }

    @Test
    void requiresOtherAgentId() {
        assertThrows(NullPointerException.class, () ->
            new EngagementEvent("a1", null, "t1", null, "turn-1", "desc",
                null, Map.of(), true, null, null, null, null, null));
    }

    @Test
    void requiresTenantId() {
        assertThrows(NullPointerException.class, () ->
            new EngagementEvent("a1", "b1", null, null, "turn-1", "desc",
                null, Map.of(), true, null, null, null, null, null));
    }

    @Test
    void requiresTurnId() {
        assertThrows(NullPointerException.class, () ->
            new EngagementEvent("a1", "b1", "t1", null, null, "desc",
                null, Map.of(), true, null, null, null, null, null));
    }

    @Test
    void rejectsBlankTurnId() {
        assertThrows(IllegalArgumentException.class, () ->
            new EngagementEvent("a1", "b1", "t1", null, "  ", "desc",
                null, Map.of(), true, null, null, null, null, null));
    }

    @Test
    void requiresDescription() {
        assertThrows(NullPointerException.class, () ->
            new EngagementEvent("a1", "b1", "t1", null, "turn-1", null,
                null, Map.of(), true, null, null, null, null, null));
    }

    @Test
    void rejectsBlankDescription() {
        assertThrows(IllegalArgumentException.class, () ->
            new EngagementEvent("a1", "b1", "t1", null, "turn-1", "  ",
                null, Map.of(), true, null, null, null, null, null));
    }

    @Test
    void rejectsSelfReferential() {
        assertThrows(IllegalArgumentException.class, () ->
            new EngagementEvent("a1", "a1", "t1", null, "turn-1", "desc",
                null, Map.of(), true, null, null, null, null, null));
    }

    @Test
    void rejectsSentimentShiftOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () ->
            new EngagementEvent("a1", "b1", "t1", null, "turn-1", "desc",
                null, Map.of(), null, null, null, 1.1, null, null));
        assertThrows(IllegalArgumentException.class, () ->
            new EngagementEvent("a1", "b1", "t1", null, "turn-1", "desc",
                null, Map.of(), null, null, null, -1.1, null, null));
    }

    @Test
    void rejectsImportanceOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () ->
            new EngagementEvent("a1", "b1", "t1", null, "turn-1", "desc",
                1.1, Map.of(), null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
            new EngagementEvent("a1", "b1", "t1", null, "turn-1", "desc",
                -0.1, Map.of(), null, null, null, null, null, null));
    }

    @Test
    void acceptsAllNullSignals() {
        var event = new EngagementEvent("a1", "b1", "t1", null, "turn-1", "desc",
            null, Map.of(), null, null, null, null, null, null);
        assertNull(event.responded());
        assertNull(event.responseTimeMs());
        assertNull(event.responseLength());
        assertNull(event.sentimentShift());
        assertNull(event.reactionCount());
        assertNull(event.continued());
    }

    @Test
    void copiesMetadata() {
        var mutable = new java.util.HashMap<>(Map.of("k", "v"));
        var event = new EngagementEvent("a1", "b1", "t1", null, "turn-1", "desc",
            null, mutable, true, null, null, null, null, null);
        mutable.put("k2", "v2");
        assertFalse(event.metadata().containsKey("k2"));
    }

    @Test
    void validEventStoresAllFields() {
        var event = new EngagementEvent("a1", "b1", "t1", "c1", "turn-1",
            "user responded enthusiastically", 0.8, Map.of("extra", "val"),
            true, 1500L, 142, 0.3, 2, true);
        assertEquals("a1", event.agentId());
        assertEquals("b1", event.otherAgentId());
        assertEquals("t1", event.tenantId());
        assertEquals("c1", event.caseId());
        assertEquals("turn-1", event.turnId());
        assertEquals("user responded enthusiastically", event.description());
        assertEquals(0.8, event.importance());
        assertTrue(event.responded());
        assertEquals(1500L, event.responseTimeMs());
        assertEquals(142, event.responseLength());
        assertEquals(0.3, event.sentimentShift());
        assertEquals(2, event.reactionCount());
        assertTrue(event.continued());
        assertEquals("val", event.metadata().get("extra"));
    }
}

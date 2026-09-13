package io.casehub.neocortex.memory.mood;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MoodStateTest {

    @Test
    void requiresAgentId() {
        assertThrows(NullPointerException.class, () ->
            new MoodState(null, "t1", null, 0.0, 0.0, 0.0, "init", null, null, Map.of()));
    }

    @Test
    void requiresTenantId() {
        assertThrows(NullPointerException.class, () ->
            new MoodState("a1", null, null, 0.0, 0.0, 0.0, "init", null, null, Map.of()));
    }

    @Test
    void requiresCause() {
        assertThrows(NullPointerException.class, () ->
            new MoodState("a1", "t1", null, 0.0, 0.0, 0.0, null, null, null, Map.of()));
    }

    @Test
    void rejectsBlankCause() {
        assertThrows(IllegalArgumentException.class, () ->
            new MoodState("a1", "t1", null, 0.0, 0.0, 0.0, "  ", null, null, Map.of()));
    }

    @Test
    void rejectsPleasureOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () ->
            new MoodState("a1", "t1", null, 1.1, 0.0, 0.0, "init", null, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
            new MoodState("a1", "t1", null, -1.1, 0.0, 0.0, "init", null, null, Map.of()));
    }

    @Test
    void rejectsArousalOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () ->
            new MoodState("a1", "t1", null, 0.0, 1.1, 0.0, "init", null, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
            new MoodState("a1", "t1", null, 0.0, -1.1, 0.0, "init", null, null, Map.of()));
    }

    @Test
    void rejectsDominanceOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () ->
            new MoodState("a1", "t1", null, 0.0, 0.0, 1.1, "init", null, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
            new MoodState("a1", "t1", null, 0.0, 0.0, -1.1, "init", null, null, Map.of()));
    }

    @Test
    void acceptsBoundaryValues() {
        var state = new MoodState("a1", "t1", null, 1.0, -1.0, 0.5, "event", "turn-1", null, Map.of());
        assertEquals(1.0, state.pleasure());
        assertEquals(-1.0, state.arousal());
        assertEquals(0.5, state.dominance());
    }

    @Test
    void acceptsNullOptionalFields() {
        var state = new MoodState("a1", "t1", null, 0.0, 0.0, 0.0, "init", null, null, Map.of());
        assertNull(state.turnId());
    }

    @Test
    void copiesMetadata() {
        var mutable = new java.util.HashMap<>(Map.of("k", "v"));
        var state = new MoodState("a1", "t1", null, 0.0, 0.0, 0.0, "init", null, null, mutable);
        mutable.put("k2", "v2");
        assertFalse(state.metadata().containsKey("k2"));
    }

    @Test
    void validStateStoresAllFields() {
        var state = new MoodState("a1", "t1", null, 0.7, -0.3, 0.5, "good news",
                                  "turn-1", null, Map.of("extra", "val"));
        assertEquals("a1", state.agentId());
        assertEquals("t1", state.tenantId());
        assertEquals(0.7, state.pleasure());
        assertEquals(-0.3, state.arousal());
        assertEquals(0.5, state.dominance());
        assertEquals("good news", state.cause());
        assertEquals("turn-1", state.turnId());
        assertEquals("val", state.metadata().get("extra"));
    }

    @Test
    void acceptsActiveContextIds() {
        var ids = Set.of("sg-work", "sg-family");
        var state = new MoodState("a1", "t1", null, 0.5, 0.0, 0.0,
                                  "test", null, ids, Map.of());
        assertEquals(ids, state.activeContextIds());
    }

    @Test
    void acceptsNullActiveContextIds() {
        var state = new MoodState("a1", "t1", null, 0.5, 0.0, 0.0,
                                  "test", null, null, Map.of());
        assertNull(state.activeContextIds());
    }

    @Test
    void defensivelyCopiesActiveContextIds() {
        var ids = new HashSet<>(Set.of("sg-work"));
        var state = new MoodState("a1", "t1", null, 0.5, 0.0, 0.0,
                                  "test", null, ids, Map.of());
        ids.add("injected");
        assertFalse(state.activeContextIds().contains("injected"));
    }
}

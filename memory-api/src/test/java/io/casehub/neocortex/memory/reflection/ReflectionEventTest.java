package io.casehub.neocortex.memory.reflection;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReflectionEventTest {

    @Test
    void requiresAgentId() {
        assertThrows(NullPointerException.class, () ->
            new ReflectionEvent(null, "t1", null, "insight", 1, List.of("m1"), null, Map.of()));
    }

    @Test
    void requiresTenantId() {
        assertThrows(NullPointerException.class, () ->
            new ReflectionEvent("a1", null, null, "insight", 1, List.of("m1"), null, Map.of()));
    }

    @Test
    void requiresInsight() {
        assertThrows(NullPointerException.class, () ->
            new ReflectionEvent("a1", "t1", null, null, 1, List.of("m1"), null, Map.of()));
    }

    @Test
    void rejectsBlankInsight() {
        assertThrows(IllegalArgumentException.class, () ->
            new ReflectionEvent("a1", "t1", null, "  ", 1, List.of("m1"), null, Map.of()));
    }

    @Test
    void rejectsLevelZero() {
        assertThrows(IllegalArgumentException.class, () ->
            new ReflectionEvent("a1", "t1", null, "insight", 0, List.of("m1"), null, Map.of()));
    }

    @Test
    void rejectsNegativeLevel() {
        assertThrows(IllegalArgumentException.class, () ->
            new ReflectionEvent("a1", "t1", null, "insight", -1, List.of("m1"), null, Map.of()));
    }

    @Test
    void requiresSourceMemoryIds() {
        assertThrows(NullPointerException.class, () ->
            new ReflectionEvent("a1", "t1", null, "insight", 1, null, null, Map.of()));
    }

    @Test
    void rejectsEmptySourceMemoryIds() {
        assertThrows(IllegalArgumentException.class, () ->
            new ReflectionEvent("a1", "t1", null, "insight", 1, List.of(), null, Map.of()));
    }

    @Test
    void rejectsInvalidImportance() {
        assertThrows(IllegalArgumentException.class, () ->
            new ReflectionEvent("a1", "t1", null, "insight", 1, List.of("m1"), 1.1, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
            new ReflectionEvent("a1", "t1", null, "insight", 1, List.of("m1"), -0.1, Map.of()));
    }

    @Test
    void acceptsNullOptionalFields() {
        var event = new ReflectionEvent("a1", "t1", null, "insight", 1, List.of("m1"), null, Map.of());
        assertNull(event.caseId());
        assertNull(event.confidence());
    }

    @Test
    void copiesSourceMemoryIds() {
        var mutable = new java.util.ArrayList<>(List.of("m1", "m2"));
        var event = new ReflectionEvent("a1", "t1", null, "insight", 1, mutable, null, Map.of());
        mutable.add("m3");
        assertEquals(2, event.sourceMemoryIds().size());
    }

    @Test
    void copiesMetadata() {
        var mutable = new java.util.HashMap<>(Map.of("k", "v"));
        var event = new ReflectionEvent("a1", "t1", null, "insight", 1, List.of("m1"), null, mutable);
        mutable.put("k2", "v2");
        assertFalse(event.metadata().containsKey("k2"));
    }

    @Test
    void validEventStoresAllFields() {
        var event = new ReflectionEvent("a1", "t1", "c1", "agents cooperate well", 2,
            List.of("m1", "m2", "m3"), 0.8, Map.of("extra", "val"));
        assertEquals("a1", event.agentId());
        assertEquals("t1", event.tenantId());
        assertEquals("c1", event.caseId());
        assertEquals("agents cooperate well", event.insight());
        assertEquals(2, event.level());
        assertEquals(List.of("m1", "m2", "m3"), event.sourceMemoryIds());
        assertEquals(0.8, event.confidence());
        assertEquals("val", event.metadata().get("extra"));
    }

    @Test
    void acceptsHighLevels() {
        var event = new ReflectionEvent("a1", "t1", null, "deep insight", 5, List.of("m1"), null, Map.of());
        assertEquals(5, event.level());
    }
}

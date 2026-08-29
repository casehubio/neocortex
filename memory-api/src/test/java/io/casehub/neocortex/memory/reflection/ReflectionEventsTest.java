package io.casehub.neocortex.memory.reflection;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReflectionEventsTest {

    @Test
    void domainIsReflection() {
        assertEquals(new MemoryDomain("reflection"), ReflectionEvents.DOMAIN);
    }

    @Test
    void toMemoryInputMapsAllFields() {
        var event = new ReflectionEvent("a1", "t1", "c1", "agents cooperate well", 2,
            List.of("m1", "m2", "m3"), 0.8, Map.of());
        MemoryInput input = ReflectionEvents.toMemoryInput(event);

        assertEquals("a1", input.entityId());
        assertEquals(ReflectionEvents.DOMAIN, input.domain());
        assertEquals("t1", input.tenantId());
        assertEquals("c1", input.caseId());
        assertEquals("agents cooperate well", input.text());
        assertEquals(0.8, input.confidence().value());
        assertEquals("2", input.attributes().get(ReflectionAttributeKeys.LEVEL));
        assertEquals("m1,m2,m3", input.attributes().get(ReflectionAttributeKeys.SOURCE_MEMORY_IDS));
    }

    @Test
    void confidenceDefaultsFromLevelWhenNull() {
        var level1 = new ReflectionEvent("a1", "t1", null, "insight", 1, List.of("m1"), null, Map.of());
        assertEquals(0.5, ReflectionEvents.toMemoryInput(level1).confidence().value(), 1e-9);

        var level2 = new ReflectionEvent("a1", "t1", null, "insight", 2, List.of("m1"), null, Map.of());
        assertEquals(0.7, ReflectionEvents.toMemoryInput(level2).confidence().value(), 1e-9);

        var level3 = new ReflectionEvent("a1", "t1", null, "insight", 3, List.of("m1"), null, Map.of());
        assertEquals(0.9, ReflectionEvents.toMemoryInput(level3).confidence().value(), 1e-9);
    }

    @Test
    void confidenceCapsAtOneForHighLevels() {
        var level5 = new ReflectionEvent("a1", "t1", null, "insight", 5, List.of("m1"), null, Map.of());
        assertEquals(1.0, ReflectionEvents.toMemoryInput(level5).confidence().value());
    }

    @Test
    void explicitImportanceOverridesDefault() {
        var event = new ReflectionEvent("a1", "t1", null, "insight", 1, List.of("m1"), 0.9, Map.of());
        assertEquals(0.9, ReflectionEvents.toMemoryInput(event).confidence().value());
    }

    @Test
    void singleSourceMemoryId() {
        var event = new ReflectionEvent("a1", "t1", null, "insight", 1, List.of("m1"), null, Map.of());
        assertEquals("m1", ReflectionEvents.toMemoryInput(event).attributes().get(ReflectionAttributeKeys.SOURCE_MEMORY_IDS));
    }

    @Test
    void callerMetadataIsMerged() {
        var event = new ReflectionEvent("a1", "t1", null, "insight", 1, List.of("m1"), null,
            Map.of("custom", "value"));
        MemoryInput input = ReflectionEvents.toMemoryInput(event);
        assertEquals("value", input.attributes().get("custom"));
    }

    @Test
    void metadataKeyCollisionThrows() {
        var event = new ReflectionEvent("a1", "t1", null, "insight", 1, List.of("m1"), null,
            Map.of(ReflectionAttributeKeys.LEVEL, "override"));
        assertThrows(IllegalArgumentException.class, () ->
            ReflectionEvents.toMemoryInput(event));
    }

    @Test
    void reflectionRecordedCarriesEventAndId() {
        var event = new ReflectionEvent("a1", "t1", null, "insight", 1, List.of("m1"), null, Map.of());
        var recorded = new ReflectionRecorded(event, "mem-1");
        assertSame(event, recorded.event());
        assertEquals("mem-1", recorded.memoryId());
    }
}

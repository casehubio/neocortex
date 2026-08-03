package io.casehub.neocortex.memory.relationship;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RelationshipEventTest {

    @Test
    void requiresAgentId() {
        assertThrows(NullPointerException.class, () ->
            new RelationshipEvent(null, "b1", "t1", null, null, "action",
                QualitySignal.NEUTRAL, "desc", null, Map.of()));
    }

    @Test
    void requiresOtherAgentId() {
        assertThrows(NullPointerException.class, () ->
            new RelationshipEvent("a1", null, "t1", null, null, "action",
                QualitySignal.NEUTRAL, "desc", null, Map.of()));
    }

    @Test
    void requiresTenantId() {
        assertThrows(NullPointerException.class, () ->
            new RelationshipEvent("a1", "b1", null, null, null, "action",
                QualitySignal.NEUTRAL, "desc", null, Map.of()));
    }

    @Test
    void requiresSourceEventType() {
        assertThrows(NullPointerException.class, () ->
            new RelationshipEvent("a1", "b1", "t1", null, null, null,
                QualitySignal.NEUTRAL, "desc", null, Map.of()));
    }

    @Test
    void rejectsBlankSourceEventType() {
        assertThrows(IllegalArgumentException.class, () ->
            new RelationshipEvent("a1", "b1", "t1", null, null, " ",
                QualitySignal.NEUTRAL, "desc", null, Map.of()));
    }

    @Test
    void requiresQualitySignal() {
        assertThrows(NullPointerException.class, () ->
            new RelationshipEvent("a1", "b1", "t1", null, null, "action",
                null, "desc", null, Map.of()));
    }

    @Test
    void requiresDescription() {
        assertThrows(NullPointerException.class, () ->
            new RelationshipEvent("a1", "b1", "t1", null, null, "action",
                QualitySignal.NEUTRAL, null, null, Map.of()));
    }

    @Test
    void rejectsBlankDescription() {
        assertThrows(IllegalArgumentException.class, () ->
            new RelationshipEvent("a1", "b1", "t1", null, null, "action",
                QualitySignal.NEUTRAL, "  ", null, Map.of()));
    }

    @Test
    void rejectsSelfReferential() {
        assertThrows(IllegalArgumentException.class, () ->
            new RelationshipEvent("a1", "a1", "t1", null, null, "action",
                QualitySignal.NEUTRAL, "desc", null, Map.of()));
    }

    @Test
    void rejectsInvalidImportance() {
        assertThrows(IllegalArgumentException.class, () ->
            new RelationshipEvent("a1", "b1", "t1", null, null, "action",
                QualitySignal.NEUTRAL, "desc", 1.1, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
            new RelationshipEvent("a1", "b1", "t1", null, null, "action",
                QualitySignal.NEUTRAL, "desc", -0.1, Map.of()));
    }

    @Test
    void acceptsNullOptionalFields() {
        var event = new RelationshipEvent("a1", "b1", "t1", null, null,
            "action", QualitySignal.NEUTRAL, "desc", null, Map.of());
        assertNull(event.caseId());
        assertNull(event.turnId());
        assertNull(event.importance());
    }

    @Test
    void copiesMetadata() {
        var mutable = new java.util.HashMap<>(Map.of("k", "v"));
        var event = new RelationshipEvent("a1", "b1", "t1", null, null,
            "action", QualitySignal.NEUTRAL, "desc", null, mutable);
        mutable.put("k2", "v2");
        assertFalse(event.metadata().containsKey("k2"));
    }

    @Test
    void qualitySignalValues() {
        assertEquals(3, QualitySignal.values().length);
        assertNotNull(QualitySignal.valueOf("POSITIVE"));
        assertNotNull(QualitySignal.valueOf("NEGATIVE"));
        assertNotNull(QualitySignal.valueOf("NEUTRAL"));
    }

    @Test
    void validEventStoresAllFields() {
        var event = new RelationshipEvent("a1", "b1", "t1", "c1", "turn-1",
            "observation", QualitySignal.POSITIVE, "cooperated on review", 0.8,
            Map.of("extra", "val"));
        assertEquals("a1", event.agentId());
        assertEquals("b1", event.otherAgentId());
        assertEquals("t1", event.tenantId());
        assertEquals("c1", event.caseId());
        assertEquals("turn-1", event.turnId());
        assertEquals("observation", event.sourceEventType());
        assertEquals(QualitySignal.POSITIVE, event.qualitySignal());
        assertEquals("cooperated on review", event.description());
        assertEquals(0.8, event.importance());
        assertEquals("val", event.metadata().get("extra"));
    }
}

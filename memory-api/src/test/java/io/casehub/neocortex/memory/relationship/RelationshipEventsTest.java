package io.casehub.neocortex.memory.relationship;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RelationshipEventsTest {

    @Test
    void domainIsRelationship() {
        assertEquals(new MemoryDomain("relationship"), RelationshipEvents.DOMAIN);
    }

    @Test
    void toMemoryInputMapsAllFields() {
        var event = new RelationshipEvent("a1", "b1", "t1", "c1", "turn-42",
                                          null, "action", QualitySignal.POSITIVE, "cooperated on review", 0.7,
                                          Map.of());
        MemoryInput input = RelationshipEvents.toMemoryInput(event);

        assertEquals("a1", input.entityId());
        assertEquals(RelationshipEvents.DOMAIN, input.domain());
        assertEquals("t1", input.tenantId());
        assertEquals("c1", input.caseId());
        assertEquals("cooperated on review", input.text());
        assertEquals(0.7, input.confidence().value());
        assertEquals("b1", input.attributes().get(RelationshipAttributeKeys.OTHER_AGENT));
        assertEquals("action", input.attributes().get(RelationshipAttributeKeys.SOURCE_EVENT_TYPE));
        assertEquals("positive", input.attributes().get(RelationshipAttributeKeys.QUALITY_SIGNAL));
        assertEquals("turn-42", input.attributes().get(RelationshipAttributeKeys.TURN_ID));
    }

    @Test
    void omitsTurnIdWhenNull() {
        var event = new RelationshipEvent("a1", "b1", "t1", null, null,
                                          null, "observation", QualitySignal.NEUTRAL, "desc", null, Map.of());
        MemoryInput input = RelationshipEvents.toMemoryInput(event);
        assertFalse(input.attributes().containsKey(RelationshipAttributeKeys.TURN_ID));
    }

    @Test
    void allQualitySignalsMappedToLowercase() {
        for (QualitySignal sig : QualitySignal.values()) {
            var event = new RelationshipEvent("a1", "b1", "t1", null, null,
                                              null, "action", sig, "desc", null, Map.of());
            MemoryInput input = RelationshipEvents.toMemoryInput(event);
            assertEquals(sig.name().toLowerCase(), input.attributes().get(RelationshipAttributeKeys.QUALITY_SIGNAL));
        }
    }

    @Test
    void callerMetadataIsMerged() {
        var event = new RelationshipEvent("a1", "b1", "t1", null, null,
                                          null, "action", QualitySignal.NEUTRAL, "desc", null,
                                          Map.of("custom-key", "custom-value"));
        MemoryInput input = RelationshipEvents.toMemoryInput(event);
        assertEquals("custom-value", input.attributes().get("custom-key"));
        assertEquals("b1", input.attributes().get(RelationshipAttributeKeys.OTHER_AGENT));
    }

    @Test
    void metadataKeyCollisionThrows() {
        var event = new RelationshipEvent("a1", "b1", "t1", null, null,
                                          null, "action", QualitySignal.NEUTRAL, "desc", null,
                                          Map.of(RelationshipAttributeKeys.OTHER_AGENT, "override"));
        assertThrows(IllegalArgumentException.class, () ->
            RelationshipEvents.toMemoryInput(event));
    }

    @Test
    void metadataCollisionOnQualitySignalThrows() {
        var event = new RelationshipEvent("a1", "b1", "t1", null, null,
                                          null, "action", QualitySignal.NEUTRAL, "desc", null,
                                          Map.of(RelationshipAttributeKeys.QUALITY_SIGNAL, "override"));
        assertThrows(IllegalArgumentException.class, () ->
            RelationshipEvents.toMemoryInput(event));
    }

    @Test
    void relationshipRecordedCarriesEventAndId() {
        var event = new RelationshipEvent("a1", "b1", "t1", null, null,
                                          null, "action", QualitySignal.NEUTRAL, "desc", null, Map.of());
        var recorded = new RelationshipRecorded(event, "mem-1");
        assertSame(event, recorded.event());
        assertEquals("mem-1", recorded.memoryId());
    }
}

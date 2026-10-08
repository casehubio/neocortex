package io.casehub.neocortex.cognition.subthought;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubThoughtsTest {

    @Test
    void subThoughtRecordValidatesType() {
        var st = new SubThought("affect-observation", "she seemed sad", "Sarah", 0.5, SubThought.Source.SYNC);
        assertEquals("affect-observation", st.type());
        assertEquals("she seemed sad", st.text());
        assertEquals("Sarah", st.entity());
        assertEquals(0.5, st.confidence());
        assertEquals(SubThought.Source.SYNC, st.source());
    }

    @Test
    void subThoughtRejectsBlankType() {
        assertThrows(IllegalArgumentException.class, () ->
            new SubThought("", "text", null, 0.5, SubThought.Source.SYNC));
    }

    @Test
    void subThoughtRejectsInvalidConfidence() {
        assertThrows(IllegalArgumentException.class, () ->
            new SubThought("concern", "text", null, 1.5, SubThought.Source.SYNC));
    }

    @Test
    void subThoughtAllowsNullEntity() {
        var st = new SubThought("intention", "should go there", null, 0.5, SubThought.Source.SYNC);
        assertNull(st.entity());
    }

    @Test
    void subThoughtResultEmptyConstant() {
        assertNotNull(SubThoughtResult.EMPTY);
        assertTrue(SubThoughtResult.EMPTY.subThoughts().isEmpty());
        assertEquals("", SubThoughtResult.EMPTY.observationHash());
        assertTrue(SubThoughtResult.EMPTY.isEmpty());
    }

    @Test
    void subThoughtResultDefensiveCopy() {
        var list = new java.util.ArrayList<SubThought>(List.of(
            new SubThought("concern", "worried", null, 0.5, SubThought.Source.SYNC)
        ));
        var result = new SubThoughtResult(list, "hash");
        list.clear();
        assertEquals(1, result.subThoughts().size());
    }

    @Test
    void mergePrefersSyncWhenNoOverlap() {
        var sync = List.of(new SubThought("intention", "should go", null, 0.5, SubThought.Source.SYNC));
        var async = List.of(new SubThought("concern", "worry about her", "Sarah", 0.8, SubThought.Source.ASYNC));
        var merged = SubThoughts.merge(sync, async);
        assertEquals(2, merged.size());
    }

    @Test
    void mergeAsyncWinsOnOverlap() {
        var sync = List.of(new SubThought("evaluative", "the food was good", null, 0.5, SubThought.Source.SYNC));
        var async = List.of(new SubThought("affect-observation", "the food was good", "restaurant", 0.8, SubThought.Source.ASYNC));
        var merged = SubThoughts.merge(sync, async);
        assertEquals(1, merged.size());
        assertEquals(SubThought.Source.ASYNC, merged.getFirst().source());
        assertEquals("affect-observation", merged.getFirst().type());
    }

    @Test
    void ofTypeFilters() {
        var list = List.of(
            new SubThought("concern", "worried", null, 0.5, SubThought.Source.SYNC),
            new SubThought("intention", "should go", null, 0.5, SubThought.Source.SYNC)
        );
        assertEquals(1, SubThoughts.ofType(list, "concern").size());
        assertEquals(0, SubThoughts.ofType(list, "evaluative").size());
    }

    @Test
    void forEntityFilters() {
        var list = List.of(
            new SubThought("concern", "worried about her", "Sarah", 0.5, SubThought.Source.SYNC),
            new SubThought("intention", "should go", null, 0.5, SubThought.Source.SYNC)
        );
        assertEquals(1, SubThoughts.forEntity(list, "Sarah").size());
        assertEquals(0, SubThoughts.forEntity(list, "Tom").size());
    }

    private static Memory memoryWith(Map<String, String> attributes) {
        return new Memory("m1", Subject.of("agent", "a1"), new MemoryDomain("experience"),
                          "t1", null, "test", attributes, Instant.now(),
                          Confidence.unknown(0.8), null, null, null, null, null);
    }

    @Test
    void extractParsesSubThoughtAttributes() {
        var attrs = new HashMap<String, String>();
        attrs.put(SubThoughtAttributeKeys.COUNT, "2");
        attrs.put(SubThoughtAttributeKeys.type(0), "affect-observation");
        attrs.put(SubThoughtAttributeKeys.text(0), "She seemed distracted");
        attrs.put(SubThoughtAttributeKeys.entity(0), "Sarah");
        attrs.put(SubThoughtAttributeKeys.confidence(0), "0.9");
        attrs.put(SubThoughtAttributeKeys.type(1), "intention");
        attrs.put(SubThoughtAttributeKeys.text(1), "Should bring David");
        attrs.put(SubThoughtAttributeKeys.confidence(1), "0.6");

        var result = SubThoughts.extract(memoryWith(attrs));
        assertEquals(2, result.size());
        assertEquals("affect-observation", result.get(0).type());
        assertEquals("She seemed distracted", result.get(0).text());
        assertEquals("Sarah", result.get(0).entity());
        assertEquals(0.9, result.get(0).confidence());
        assertEquals(SubThought.Source.ASYNC, result.get(0).source());
        assertEquals("intention", result.get(1).type());
        assertNull(result.get(1).entity());
        assertEquals(0.6, result.get(1).confidence());
    }

    @Test
    void extractReturnsEmptyWhenNoCountAttribute() {
        var result = SubThoughts.extract(memoryWith(Map.of()));
        assertTrue(result.isEmpty());
    }

    @Test
    void extractReturnsEmptyWhenCountMalformed() {
        var result = SubThoughts.extract(memoryWith(Map.of(SubThoughtAttributeKeys.COUNT, "abc")));
        assertTrue(result.isEmpty());
    }

    @Test
    void extractSkipsEntriesWithMissingTypeOrText() {
        var attrs = new HashMap<String, String>();
        attrs.put(SubThoughtAttributeKeys.COUNT, "2");
        attrs.put(SubThoughtAttributeKeys.type(0), "concern");
        // text(0) missing — should be skipped
        attrs.put(SubThoughtAttributeKeys.type(1), "intention");
        attrs.put(SubThoughtAttributeKeys.text(1), "plan to go");
        attrs.put(SubThoughtAttributeKeys.confidence(1), "0.7");

        var result = SubThoughts.extract(memoryWith(attrs));
        assertEquals(1, result.size());
        assertEquals("intention", result.getFirst().type());
    }

    @Test
    void extractDefaultsConfidenceWhenMissing() {
        var attrs = new HashMap<String, String>();
        attrs.put(SubThoughtAttributeKeys.COUNT, "1");
        attrs.put(SubThoughtAttributeKeys.type(0), "concern");
        attrs.put(SubThoughtAttributeKeys.text(0), "worried");
        // no confidence attribute

        var result = SubThoughts.extract(memoryWith(attrs));
        assertEquals(1, result.size());
        assertEquals(0.8, result.getFirst().confidence());
    }

    @Test
    void extractDefaultsConfidenceWhenMalformed() {
        var attrs = new HashMap<String, String>();
        attrs.put(SubThoughtAttributeKeys.COUNT, "1");
        attrs.put(SubThoughtAttributeKeys.type(0), "concern");
        attrs.put(SubThoughtAttributeKeys.text(0), "worried");
        attrs.put(SubThoughtAttributeKeys.confidence(0), "not-a-number");

        var result = SubThoughts.extract(memoryWith(attrs));
        assertEquals(1, result.size());
        assertEquals(0.8, result.getFirst().confidence());
    }

}

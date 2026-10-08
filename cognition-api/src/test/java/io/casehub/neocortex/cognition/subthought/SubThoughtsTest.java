package io.casehub.neocortex.cognition.subthought;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
}

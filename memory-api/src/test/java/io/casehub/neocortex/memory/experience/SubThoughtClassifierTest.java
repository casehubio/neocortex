package io.casehub.neocortex.memory.experience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubThoughtClassifierTest {

    @Test
    void classifiesAffectObservation() {
        var matches = SubThoughtClassifier.classify("Sarah seemed really upset today.");
        assertEquals(1, matches.size());
        assertEquals(SubThoughtTypes.AFFECT_OBSERVATION, matches.getFirst().type());
    }

    @Test
    void classifiesCausalInference() {
        var matches = SubThoughtClassifier.classify("She is stressed because of the deadline.");
        assertEquals(1, matches.size());
        assertEquals(SubThoughtTypes.CAUSAL_INFERENCE, matches.getFirst().type());
    }

    @Test
    void classifiesIntention() {
        var matches = SubThoughtClassifier.classify("I should bring David next time.");
        assertEquals(1, matches.size());
        assertEquals(SubThoughtTypes.INTENTION, matches.getFirst().type());
    }

    @Test
    void classifiesConcern() {
        var matches = SubThoughtClassifier.classify("I worry about her coping.");
        assertEquals(1, matches.size());
        assertEquals(SubThoughtTypes.CONCERN, matches.getFirst().type());
    }

    @Test
    void classifiesMultipleSentences() {
        var matches = SubThoughtClassifier.classify("Sarah seemed sad. I worry about her. The weather was nice.");
        assertEquals(2, matches.size());
        assertEquals(SubThoughtTypes.AFFECT_OBSERVATION, matches.get(0).type());
        assertEquals(SubThoughtTypes.CONCERN, matches.get(1).type());
    }

    @Test
    void skipsUnmatchedSentences() {
        var matches = SubThoughtClassifier.classify("We went to the park. The sun was shining.");
        assertTrue(matches.isEmpty());
    }

    @Test
    void nullInputReturnsEmpty() {
        assertTrue(SubThoughtClassifier.classify(null).isEmpty());
    }

    @Test
    void blankInputReturnsEmpty() {
        assertTrue(SubThoughtClassifier.classify("   ").isEmpty());
    }

    @Test
    void bestMatchWinsWhenMultipleTypesMatch() {
        var matches = SubThoughtClassifier.classify("I felt sad and worried and afraid.");
        assertEquals(1, matches.size());
        // "worried", "afraid" are concern keywords (2 matches);
        // "felt", "sad" are affect keywords (2 matches); tie-break is map iteration order
        String type = matches.getFirst().type();
        assertTrue(
            SubThoughtTypes.AFFECT_OBSERVATION.equals(type) || SubThoughtTypes.CONCERN.equals(type),
            "Expected affect-observation or concern, got: " + type);
    }

    @Test
    void containsWordRespectsWordBoundaries() {
        assertFalse(SubThoughtClassifier.containsWord("gooder", "good"));
        assertTrue(SubThoughtClassifier.containsWord("that is good", "good"));
        assertTrue(SubThoughtClassifier.containsWord("good stuff", "good"));
    }

    @Test
    void preservesOriginalSentenceText() {
        var matches = SubThoughtClassifier.classify("Sarah SEEMED really upset.");
        assertEquals("Sarah SEEMED really upset.", matches.getFirst().text());
    }
}

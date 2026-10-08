package io.casehub.neocortex.cognition.subthought;

import io.casehub.neocortex.memory.experience.SubThoughtTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedSubThoughtExtractorTest {

    private RuleBasedSubThoughtExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new RuleBasedSubThoughtExtractor();
    }

    @Test
    void extractsAffectObservation() {
        var results = extractor.extract("Sarah seemed really upset today.", "agent1", "tenant1");
        assertEquals(1, results.size());
        assertEquals(SubThoughtTypes.AFFECT_OBSERVATION, results.getFirst().type());
        assertEquals(SubThought.Source.SYNC, results.getFirst().source());
        assertEquals(0.5, results.getFirst().confidence());
    }

    @Test
    void extractsCausalInference() {
        var results = extractor.extract("She is stressed because of the promotion.", "agent1", "tenant1");
        assertEquals(1, results.size());
        assertEquals(SubThoughtTypes.CAUSAL_INFERENCE, results.getFirst().type());
    }

    @Test
    void extractsIntention() {
        var results = extractor.extract("I should bring David here next time.", "agent1", "tenant1");
        assertEquals(1, results.size());
        assertEquals(SubThoughtTypes.INTENTION, results.getFirst().type());
    }

    @Test
    void extractsConcern() {
        var results = extractor.extract("I worry she is not coping well.", "agent1", "tenant1");
        assertEquals(1, results.size());
        assertEquals(SubThoughtTypes.CONCERN, results.getFirst().type());
    }

    @Test
    void extractsSelfReflection() {
        var results = extractor.extract("I wonder if that was the right thing to do.", "agent1", "tenant1");
        assertEquals(1, results.size());
        assertEquals(SubThoughtTypes.SELF_REFLECTION, results.getFirst().type());
    }

    @Test
    void extractsEvaluative() {
        var results = extractor.extract("The pasta was excellent.", "agent1", "tenant1");
        assertEquals(1, results.size());
        assertEquals(SubThoughtTypes.EVALUATIVE, results.getFirst().type());
    }

    @Test
    void extractsAssociation() {
        var results = extractor.extract("This reminds me of our last trip.", "agent1", "tenant1");
        assertEquals(1, results.size());
        assertEquals(SubThoughtTypes.ASSOCIATION, results.getFirst().type());
    }

    @Test
    void extractsMultipleSentences() {
        var results = extractor.extract("Sarah seemed sad. I worry about her. The food was excellent.", "agent1", "tenant1");
        assertEquals(3, results.size());
    }

    @Test
    void skipsUnmatchedSentences() {
        var results = extractor.extract("We went to the park. The weather was nice.", "agent1", "tenant1");
        assertTrue(results.isEmpty());
    }

    @Test
    void entityNameMatching() {
        extractor.refreshEntityCache("tenant1", Set.of("Sarah", "David"));
        var results = extractor.extract("Sarah seemed upset.", "agent1", "tenant1");
        assertEquals(1, results.size());
        assertEquals("Sarah", results.getFirst().entity());
    }

    @Test
    void entityMatchingCaseInsensitive() {
        extractor.refreshEntityCache("tenant1", Set.of("Sarah"));
        var results = extractor.extract("I think sarah seemed upset.", "agent1", "tenant1");
        assertEquals("Sarah", results.getFirst().entity());
    }

    @Test
    void noEntityWhenCacheEmpty() {
        var results = extractor.extract("Sarah seemed upset.", "agent1", "tenant1");
        assertNull(results.getFirst().entity());
    }

    @Test
    void emptyObservationReturnsEmpty() {
        assertTrue(extractor.extract("", "agent1", "tenant1").isEmpty());
        assertTrue(extractor.extract(null, "agent1", "tenant1").isEmpty());
    }

    @Test
    void highestKeywordCountWins() {
        // "concerned and afraid and scared" has 3 concern keywords vs any other type
        var results = extractor.extract("I am concerned and afraid and scared.", "agent1", "tenant1");
        assertEquals(1, results.size());
        assertEquals(SubThoughtTypes.CONCERN, results.getFirst().type());
    }
}

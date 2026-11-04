package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PayloadFilter;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CorpusQueryTest {

    @Test
    void textQueryHasNullVector() {
        CorpusQuery q = CorpusQuery.of("search term");
        assertEquals("search term", q.text());
        assertNull(q.embeddingVector());
    }

    @Test
    void vectorQueryHasNullText() {
        float[] vec = {0.1f, 0.2f, 0.3f};
        CorpusQuery q = CorpusQuery.ofVector(vec);
        assertNull(q.text());
        assertArrayEquals(vec, q.embeddingVector());
        assertEquals(10, q.maxResults());
    }

    @Test
    void vectorQueryWithMaxResults() {
        float[] vec = {0.1f, 0.2f};
        CorpusQuery q = CorpusQuery.ofVector(vec, 5);
        assertNull(q.text());
        assertArrayEquals(vec, q.embeddingVector());
        assertEquals(5, q.maxResults());
    }

    @Test
    void hybridQueryHasTextAndVector() {
        float[] vec = {0.1f, 0.2f};
        CorpusQuery q = CorpusQuery.of("term").withEmbeddingVector(vec);
        assertEquals("term", q.text());
        assertArrayEquals(vec, q.embeddingVector());
    }

    @Test
    void rejectsAllNullQuery() {
        assertThrows(IllegalArgumentException.class,
            () -> new CorpusQuery(null, null, null, Set.of(), 10, -1, false, Map.of()));
    }

    @Test
    void filterOnlyQueryIsValid() {
        var filter = PayloadFilter.eq("domain", "test");
        CorpusQuery q = new CorpusQuery(null, null, filter, Set.of(), 10, -1, false, Map.of());
        assertNull(q.text());
        assertNull(q.embeddingVector());
        assertNotNull(q.filter());
    }

    @Test
    void withMaxResultsProducesCorrectCopy() {
        CorpusQuery q = CorpusQuery.of("term").withMaxResults(25);
        assertEquals("term", q.text());
        assertEquals(25, q.maxResults());
    }

    @Test
    void withCorpusWeightsProducesCorrectCopy() {
        CorpusRef ref = new CorpusRef("tenant", "corpus");
        CorpusQuery q = CorpusQuery.of("term").withCorpusWeights(Map.of(ref, 2.0));
        assertEquals(Map.of(ref, 2.0), q.corpusWeights());
    }

    @Test
    void existingOfFactoryBackwardCompatible() {
        CorpusQuery q = CorpusQuery.of("term");
        assertEquals("term", q.text());
        assertNull(q.embeddingVector());
        assertNull(q.filter());
        assertEquals(Set.of(), q.corpusRefs());
        assertEquals(10, q.maxResults());
        assertEquals(-1, q.federationDepth());
        assertFalse(q.includeArchived());
        assertEquals(Map.of(), q.corpusWeights());
    }

    @Test
    void existingOfWithMaxResultsBackwardCompatible() {
        CorpusQuery q = CorpusQuery.of("term", 20);
        assertEquals("term", q.text());
        assertEquals(20, q.maxResults());
        assertNull(q.embeddingVector());
    }
}

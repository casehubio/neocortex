package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConvexCombinationFusionTest {

    @Test
    void fusesTwoLegsWithWeights() {
        var dense = List.of(chunk("a", 0.9), chunk("b", 0.7), chunk("c", 0.5));
        var sparse = List.of(chunk("b", 0.8), chunk("d", 0.6), chunk("a", 0.4));
        var legs = List.of(
            new ConvexCombinationFusion.ScoredLeg(dense, 0.6),
            new ConvexCombinationFusion.ScoredLeg(sparse, 0.4));

        List<RetrievedChunk> result = ConvexCombinationFusion.fuse(legs, 10);

        // "b" is in both legs — should rank highest
        assertEquals("b", result.get(0).sourceDocumentId());
        assertTrue(result.get(0).relevanceScore() > result.get(1).relevanceScore());
    }

    @Test
    void renormalizesWeightsForAbsentLegs() {
        // Only one leg present out of configured weights — weight should be 1.0
        var dense = List.of(chunk("a", 0.9));
        var legs = List.of(new ConvexCombinationFusion.ScoredLeg(dense, 0.5));

        List<RetrievedChunk> result = ConvexCombinationFusion.fuse(legs, 10);
        assertEquals(1.0, result.get(0).relevanceScore(), 1e-5);
    }

    @Test
    void handlesEqualScoresWithoutNaN() {
        var leg = List.of(chunk("a", 0.5), chunk("b", 0.5));
        var legs = List.of(new ConvexCombinationFusion.ScoredLeg(leg, 1.0));

        List<RetrievedChunk> result = ConvexCombinationFusion.fuse(legs, 10);
        assertFalse(Double.isNaN(result.get(0).relevanceScore()));
        assertEquals(1.0, result.get(0).relevanceScore(), 1e-5);
    }

    @Test
    void deduplicatesBySourceDocumentIdAndContent() {
        var leg1 = List.of(chunk("a", 0.9), chunk("a", 0.7));  // Same id+content
        var legs = List.of(new ConvexCombinationFusion.ScoredLeg(leg1, 1.0));

        List<RetrievedChunk> result = ConvexCombinationFusion.fuse(legs, 10);
        assertEquals(1, result.size());  // Deduplicated
    }

    @Test
    void preservesBestGrade() {
        var leg1 = List.of(
            new RetrievedChunk("content", "a", 0.9, Map.of(), RelevanceGrade.INCORRECT));
        var leg2 = List.of(
            new RetrievedChunk("content", "a", 0.8, Map.of(), RelevanceGrade.CORRECT));
        var legs = List.of(
            new ConvexCombinationFusion.ScoredLeg(leg1, 0.5),
            new ConvexCombinationFusion.ScoredLeg(leg2, 0.5));

        List<RetrievedChunk> result = ConvexCombinationFusion.fuse(legs, 10);
        assertEquals(1, result.size());
        assertEquals(RelevanceGrade.CORRECT, result.get(0).grade());
    }

    @Test
    void returnsEmptyForEmptyLegs() {
        List<RetrievedChunk> result = ConvexCombinationFusion.fuse(List.of(), 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void respectsMaxResults() {
        var leg = List.of(chunk("a", 0.9), chunk("b", 0.7), chunk("c", 0.5));
        var legs = List.of(new ConvexCombinationFusion.ScoredLeg(leg, 1.0));

        List<RetrievedChunk> result = ConvexCombinationFusion.fuse(legs, 2);
        assertEquals(2, result.size());
    }

    @Test
    void fuseSingleChunkAtZeroScoreNormalizesToOne() {
        var leg = List.of(chunk("a", 0.0));
        var legs = List.of(new ConvexCombinationFusion.ScoredLeg(leg, 1.0));
        List<RetrievedChunk> result = ConvexCombinationFusion.fuse(legs, 10);
        assertEquals(1, result.size());
        assertEquals(1.0, result.get(0).relevanceScore(), 1e-5);
    }

    private RetrievedChunk chunk(String id, double score) {
        return new RetrievedChunk(id, id, score, Map.of());
    }
}

package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MinHashIndexTest {

    @Test
    void identicalSetsProduceIdenticalSignatures() {
        var index = new MinHashIndex(128);
        long[] sig1 = index.signature(Set.of("a", "b", "c"));
        long[] sig2 = index.signature(Set.of("a", "b", "c"));
        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    void approximateJaccardCloseToExactForHighOverlap() {
        var index = new MinHashIndex(256);
        Set<String> a = Set.of("d1", "d2", "d3", "d4", "d5");
        Set<String> b = Set.of("d1", "d2", "d3", "d4", "d6");
        // Exact Jaccard = 4/6 ≈ 0.667
        double approx = index.approximateJaccard(index.signature(a), index.signature(b));
        assertThat(approx).isCloseTo(4.0 / 6, within(0.15));
    }

    @Test
    void disjointSetsProduceLowSimilarity() {
        var index = new MinHashIndex(128);
        long[] sig1 = index.signature(Set.of("a", "b", "c"));
        long[] sig2 = index.signature(Set.of("x", "y", "z"));
        double approx = index.approximateJaccard(sig1, sig2);
        assertThat(approx).isLessThan(0.25);
    }

    @Test
    void candidatePairsIncludeHighSimilarityPairs() {
        var index = new MinHashIndex(128);
        Map<String, Set<String>> docSets = new HashMap<>();
        docSets.put("q1", Set.of("d1", "d2", "d3", "d4"));
        docSets.put("q2", Set.of("d1", "d2", "d3", "d5"));  // J(q1,q2) = 3/5 = 0.6
        docSets.put("q3", Set.of("d10", "d11", "d12"));       // J(q1,q3) ≈ 0
        var candidates = index.candidatePairs(docSets, 0.4);
        assertThat(candidates).anyMatch(p ->
            (p.a().equals("q1") && p.b().equals("q2")) ||
            (p.a().equals("q2") && p.b().equals("q1")));
    }

    @Test
    void candidatePairsExcludeDisjointPairs() {
        var index = new MinHashIndex(128);
        Map<String, Set<String>> docSets = new HashMap<>();
        docSets.put("q1", Set.of("d1", "d2", "d3"));
        docSets.put("q2", Set.of("d10", "d11", "d12"));
        var candidates = index.candidatePairs(docSets, 0.5);
        assertThat(candidates).noneMatch(p ->
            (p.a().equals("q1") && p.b().equals("q2")) ||
            (p.a().equals("q2") && p.b().equals("q1")));
    }

    @Test
    void largeSyntheticSetProducesCandidatesEfficiently() {
        var index = new MinHashIndex(128);
        Map<String, Set<String>> docSets = new HashMap<>();
        // 100 queries, each with ~10 docs; first 50 share docs, last 50 disjoint
        for (int i = 0; i < 50; i++) {
            Set<String> docs = new HashSet<>();
            docs.add("shared1"); docs.add("shared2");
            for (int j = 0; j < 8; j++) docs.add("group1-d" + i + "-" + j);
            docSets.put("q" + i, docs);
        }
        for (int i = 50; i < 100; i++) {
            Set<String> docs = new HashSet<>();
            for (int j = 0; j < 10; j++) docs.add("group2-d" + i + "-" + j);
            docSets.put("q" + i, docs);
        }
        var candidates = index.candidatePairs(docSets, 0.1);
        // Should find candidates within group1 (shared docs), few/none across groups
        boolean anyInGroup1 = candidates.stream().anyMatch(p ->
            p.a().startsWith("q") && Integer.parseInt(p.a().substring(1)) < 50 &&
            p.b().startsWith("q") && Integer.parseInt(p.b().substring(1)) < 50);
        assertThat(anyInGroup1).isTrue();
    }
}

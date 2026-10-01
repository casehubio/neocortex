package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TextSimilarityCorroboratorTest {

    private final TextSimilarityCorroborator corroborator =
            new TextSimilarityCorroborator(null, 0.8, 0.5);

    private Memory memory(String id, String text) {
        return new Memory(id, Subject.of("person", "alice"),
                new MemoryDomain("experience"), "t1", null,
                text, Map.of(), Instant.now(),
                Confidence.unknown(0.8), null, null, null, null, Set.of());
    }

    @Test
    void emptyCandiates_returnsZero() {
        assertThat(corroborator.countSimilar(memory("m1", "hello world"), List.of())).isZero();
    }

    @Test
    void nullText_returnsZero() {
        Memory target = new Memory("m1", Subject.of("person", "alice"),
                new MemoryDomain("experience"), "t1", null,
                null, Map.of(), Instant.now(),
                null, null, null, null, null, Set.of());
        assertThat(corroborator.countSimilar(target, List.of(memory("m2", "text")))).isZero();
    }

    @Test
    void blankText_returnsZero() {
        assertThat(corroborator.countSimilar(memory("m1", "  "), List.of(memory("m2", "text")))).isZero();
    }

    @Test
    void sameMemoryId_excluded() {
        Memory m1 = memory("m1", "the quick brown fox jumps over lazy dog");
        assertThat(corroborator.countByKeywordOverlap(m1, List.of(m1))).isZero();
    }

    @Test
    void highOverlap_counted() {
        Memory target = memory("m1", "the user reported slow database query performance");
        Memory similar = memory("m2", "user reported very slow database query latency");
        Memory different = memory("m3", "the weather is sunny and warm today");
        assertThat(corroborator.countByKeywordOverlap(target, List.of(similar, different))).isEqualTo(1);
    }

    @Test
    void multipleMatches_allCounted() {
        Memory target = memory("m1", "the system crashed due to memory overflow error");
        Memory sim1 = memory("m2", "system crashed because of memory overflow in the service");
        Memory sim2 = memory("m3", "the system crashed with memory overflow exception");
        Memory diff = memory("m4", "user login was successful with no issues");
        assertThat(corroborator.countByKeywordOverlap(target, List.of(sim1, sim2, diff))).isEqualTo(2);
    }

    @Test
    void jaccardSimilarity_identicalSets() {
        Set<String> a = Set.of("hello", "world");
        assertThat(TextSimilarityCorroborator.jaccardSimilarity(a, a)).isEqualTo(1.0);
    }

    @Test
    void jaccardSimilarity_disjointSets() {
        assertThat(TextSimilarityCorroborator.jaccardSimilarity(
                Set.of("hello", "world"), Set.of("foo", "bar"))).isEqualTo(0.0);
    }

    @Test
    void jaccardSimilarity_partialOverlap() {
        double sim = TextSimilarityCorroborator.jaccardSimilarity(
                Set.of("hello", "world", "foo"), Set.of("hello", "world", "bar"));
        assertThat(sim).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void tokenize_filtersShortWords() {
        Set<String> tokens = TextSimilarityCorroborator.tokenize("I am a big fan of AI");
        assertThat(tokens).contains("big", "fan").doesNotContain("am", "a", "i", "of", "ai");
    }

    @Test
    void tokenize_lowercases() {
        Set<String> tokens = TextSimilarityCorroborator.tokenize("Hello World Test");
        assertThat(tokens).contains("hello", "world", "test");
    }

    @Test
    void fallsBackToKeyword_whenNoEmbeddingModel() {
        Memory target = memory("m1", "the user reported slow database query performance");
        Memory similar = memory("m2", "user reported very slow database query latency");
        assertThat(corroborator.countSimilar(target, List.of(similar))).isEqualTo(1);
    }
}

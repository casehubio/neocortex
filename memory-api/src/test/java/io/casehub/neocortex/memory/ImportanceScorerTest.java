package io.casehub.neocortex.memory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImportanceScorerTest {

    @Test
    void noOpScorerReturnsEmptyMap() {
        ImportanceScorer noOp = batch -> Map.of();
        var result = noOp.score(List.of(
            new ImportanceScorer.ScoringEntry("m1", "some text", "experience", "t1")));
        assertThat(result).isEmpty();
    }

    @Test
    void scorerReturnsSubsetOfBatch() {
        ImportanceScorer partial = batch -> Map.of(batch.get(0).memoryId(), 7);
        var result = partial.score(List.of(
            new ImportanceScorer.ScoringEntry("m1", "text1", "experience", "t1"),
            new ImportanceScorer.ScoringEntry("m2", "text2", "experience", "t1")));
        assertThat(result).containsOnlyKeys("m1");
        assertThat(result.get("m1")).isEqualTo(7);
    }
}

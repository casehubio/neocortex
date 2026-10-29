package io.casehub.neocortex.memory.importance;

import io.casehub.neocortex.memory.ImportanceScorer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmImportanceScorerTest {

    @Test
    void parsesValidJsonResponse() {
        var scorer = new LlmImportanceScorer(prompt ->
            "[{\"index\": 0, \"score\": 8}, {\"index\": 1, \"score\": 3}]");

        var result = scorer.score(List.of(
            new ImportanceScorer.ScoringEntry("m1", "Enemy rushed with 6-pool", "observation", "t1"),
            new ImportanceScorer.ScoringEntry("m2", "Built a supply depot", "action", "t1")));

        assertThat(result).containsEntry("m1", 8).containsEntry("m2", 3);
    }

    @Test
    void clampsOutOfRangeScores() {
        var scorer = new LlmImportanceScorer(prompt ->
            "[{\"index\": 0, \"score\": 15}, {\"index\": 1, \"score\": -2}]");

        var result = scorer.score(List.of(
            new ImportanceScorer.ScoringEntry("m1", "text1", "observation", "t1"),
            new ImportanceScorer.ScoringEntry("m2", "text2", "action", "t1")));

        assertThat(result.get("m1")).isEqualTo(10);
        assertThat(result.get("m2")).isEqualTo(1);
    }

    @Test
    void handlesPartialParseFailure() {
        var scorer = new LlmImportanceScorer(prompt ->
            "[{\"index\": 0, \"score\": 7}, {\"garbage\": true}]");

        var result = scorer.score(List.of(
            new ImportanceScorer.ScoringEntry("m1", "text1", "observation", "t1"),
            new ImportanceScorer.ScoringEntry("m2", "text2", "action", "t1")));

        assertThat(result).containsEntry("m1", 7);
        assertThat(result).doesNotContainKey("m2");
    }

    @Test
    void returnsEmptyOnTotalParseFailure() {
        var scorer = new LlmImportanceScorer(prompt -> "not json at all");

        var result = scorer.score(List.of(
            new ImportanceScorer.ScoringEntry("m1", "text", "observation", "t1")));

        assertThat(result).isEmpty();
    }

    @Test
    void emptyBatchReturnsEmpty() {
        var scorer = new LlmImportanceScorer(prompt -> "[]");
        assertThat(scorer.score(List.of())).isEmpty();
    }

    @Test
    void llmCallFailureReturnsEmpty() {
        var scorer = new LlmImportanceScorer(prompt -> { throw new RuntimeException("LLM down"); });

        var result = scorer.score(List.of(
            new ImportanceScorer.ScoringEntry("m1", "text", "observation", "t1")));

        assertThat(result).isEmpty();
    }
}

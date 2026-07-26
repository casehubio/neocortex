package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ColBertRelevanceEvaluatorTest {

    private static final double CORRECT = 0.55;
    private static final double INCORRECT = 0.35;

    private final ColBertRelevanceEvaluator evaluator =
        new ColBertRelevanceEvaluator(CORRECT, INCORRECT);

    @ParameterizedTest
    @CsvSource({
        "0.9,  CORRECT",
        "0.55, CORRECT",
        "0.5,  AMBIGUOUS",
        "0.4,  AMBIGUOUS",
        "0.35, INCORRECT",
        "0.1,  INCORRECT",
        "0.0,  INCORRECT",
    })
    void gradeFromScore(double score, RelevanceGrade expected) {
        var chunks = List.of(new RetrievedChunk("text", "doc1", score, Map.of()));
        var results = evaluator.evaluateChunks("query", chunks);
        assertThat(results.get(0).grade()).isEqualTo(expected);
    }

    @Test
    void scoreIsPassedThrough() {
        var chunks = List.of(new RetrievedChunk("text", "doc1", 0.75, Map.of()));
        var results = evaluator.evaluateChunks("query", chunks);
        assertThat(results.get(0).score()).isEqualTo(0.75f, within(0.001f));
    }

    @Test
    void emptyChunksReturnsEmpty() {
        assertThat(evaluator.evaluateChunks("query", List.of())).isEmpty();
    }

    @Test
    void multipleChunksGradedIndependently() {
        var chunks = List.of(
            new RetrievedChunk("high", "d1", 0.8, Map.of()),
            new RetrievedChunk("mid", "d2", 0.45, Map.of()),
            new RetrievedChunk("low", "d3", 0.2, Map.of()));
        var results = evaluator.evaluateChunks("query", chunks);
        assertThat(results).extracting(ScoredGrade::grade)
            .containsExactly(RelevanceGrade.CORRECT, RelevanceGrade.AMBIGUOUS, RelevanceGrade.INCORRECT);
    }

    @Test
    void negativeScore() {
        var chunks = List.of(new RetrievedChunk("text", "d1", -0.5, Map.of()));
        var results = evaluator.evaluateChunks("query", chunks);
        assertThat(results.get(0).grade()).isEqualTo(RelevanceGrade.INCORRECT);
    }

    @Test
    void constructorRejectsInvertedThresholds() {
        assertThatThrownBy(() -> new ColBertRelevanceEvaluator(0.3, 0.7))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalThresholdsAllowed() {
        var eval = new ColBertRelevanceEvaluator(0.5, 0.5);
        var chunks = List.of(
            new RetrievedChunk("a", "d1", 0.5, Map.of()),
            new RetrievedChunk("b", "d2", 0.4, Map.of()));
        var results = eval.evaluateChunks("query", chunks);
        assertThat(results.get(0).grade()).isEqualTo(RelevanceGrade.CORRECT);
        assertThat(results.get(1).grade()).isEqualTo(RelevanceGrade.INCORRECT);
    }
}

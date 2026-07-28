package io.casehub.neocortex.rag.crossencoder.corrective;

import io.casehub.neocortex.inference.InferenceInput;
import io.casehub.neocortex.inference.inmem.InMemoryInferenceModel;
import io.casehub.neocortex.inference.tasks.CrossEncoderReranker;
import io.casehub.neocortex.rag.RelevanceGrade;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.ScoredGrade;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrossEncoderRelevanceEvaluatorTest {

    @Test
    void scoreAboveCorrectThresholdReturnsCorrect() {
        var evaluator = evaluatorReturningScore(0.85f, 0.7, 0.3);
        var results   = evaluator.evaluateChunks("query", List.of(chunk("chunk")));
        assertThat(results.get(0).grade()).isEqualTo(RelevanceGrade.CORRECT);
    }

    @Test
    void scoreAtCorrectThresholdReturnsCorrect() {
        var evaluator = evaluatorReturningScore(0.7f, 0.7, 0.3);
        var results   = evaluator.evaluateChunks("query", List.of(chunk("chunk")));
        assertThat(results.get(0).grade()).isEqualTo(RelevanceGrade.CORRECT);
    }

    @Test
    void scoreBelowIncorrectThresholdReturnsIncorrect() {
        var evaluator = evaluatorReturningScore(0.1f, 0.7, 0.3);
        var results   = evaluator.evaluateChunks("query", List.of(chunk("chunk")));
        assertThat(results.get(0).grade()).isEqualTo(RelevanceGrade.INCORRECT);
    }

    @Test
    void scoreAtIncorrectThresholdReturnsIncorrect() {
        var evaluator = evaluatorReturningScore(0.3f, 0.7, 0.3);
        var results   = evaluator.evaluateChunks("query", List.of(chunk("chunk")));
        assertThat(results.get(0).grade()).isEqualTo(RelevanceGrade.INCORRECT);
    }

    @Test
    void scoreBetweenThresholdsReturnsAmbiguous() {
        var evaluator = evaluatorReturningScore(0.5f, 0.7, 0.3);
        var results   = evaluator.evaluateChunks("query", List.of(chunk("chunk")));
        assertThat(results.get(0).grade()).isEqualTo(RelevanceGrade.AMBIGUOUS);
    }

    @Test
    void evaluateChunksExtractsContentAndGrades() {
        var model     = contentScoringModel(Map.of("good", 0.9f, "meh", 0.5f, "bad", 0.1f));
        var reranker  = new CrossEncoderReranker(model);
        var evaluator = new CrossEncoderRelevanceEvaluator(reranker, 0.7, 0.3);

        var chunks  = List.of(chunk("good"), chunk("meh"), chunk("bad"));
        var results = evaluator.evaluateChunks("query", chunks);

        assertThat(results).extracting(ScoredGrade::grade).containsExactly(
                RelevanceGrade.CORRECT, RelevanceGrade.AMBIGUOUS, RelevanceGrade.INCORRECT);
    }

    @Test
    void evaluateChunksReturnsScores() {
        var model = contentScoringModel(Map.of(
                "correct", 0.9f, "incorrect", 0.1f, "ambiguous", 0.5f));
        var reranker  = new CrossEncoderReranker(model);
        var evaluator = new CrossEncoderRelevanceEvaluator(reranker, 0.7, 0.3);

        var chunks  = List.of(chunk("correct"), chunk("incorrect"), chunk("ambiguous"));
        var results = evaluator.evaluateChunks("query", chunks);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).grade()).isEqualTo(RelevanceGrade.CORRECT);
        assertThat(results.get(0).score()).isEqualTo(0.9f);
        assertThat(results.get(1).grade()).isEqualTo(RelevanceGrade.INCORRECT);
        assertThat(results.get(1).score()).isEqualTo(0.1f);
        assertThat(results.get(2).grade()).isEqualTo(RelevanceGrade.AMBIGUOUS);
        assertThat(results.get(2).score()).isEqualTo(0.5f);
    }

    @Test
    void evaluateChunksEmptyReturnsEmpty() {
        var model     = InMemoryInferenceModel.returning(0.5f);
        var reranker  = new CrossEncoderReranker(model);
        var evaluator = new CrossEncoderRelevanceEvaluator(reranker, 0.7, 0.3);
        assertThat(evaluator.evaluateChunks("query", List.of())).isEmpty();
    }

    @Test
    void constructorRejectsNullReranker() {
        assertThatThrownBy(() -> new CrossEncoderRelevanceEvaluator(null, 0.7, 0.3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsInvertedThresholds() {
        var model    = InMemoryInferenceModel.returning(0.5f);
        var reranker = new CrossEncoderReranker(model);
        assertThatThrownBy(() -> new CrossEncoderRelevanceEvaluator(reranker, 0.3, 0.7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RetrievedChunk chunk(String content) {
        return new RetrievedChunk(content, "doc1", 0.5, Map.of());
    }

    private static CrossEncoderRelevanceEvaluator evaluatorReturningScore(
            float score, double correctThreshold, double incorrectThreshold) {
        var model    = InMemoryInferenceModel.returning(score);
        var reranker = new CrossEncoderReranker(model);
        return new CrossEncoderRelevanceEvaluator(reranker, correctThreshold, incorrectThreshold);
    }

    private static InMemoryInferenceModel contentScoringModel(
            Map<String, Float> contentToScore) {
        return InMemoryInferenceModel.withFunction(1, input -> {
            String candidate = ((InferenceInput.Text) input).texts().get(1);
            Float  score     = contentToScore.get(candidate);
            return new float[]{score != null ? score : 0.0f};
        });
    }
}

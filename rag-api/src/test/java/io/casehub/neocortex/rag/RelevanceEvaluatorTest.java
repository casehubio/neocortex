package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RelevanceEvaluatorTest {

    @Test
    void evaluateChunksReturnsGradePerChunk() {
        RelevanceEvaluator evaluator = (query, chunks) -> chunks.stream()
                                                                .map(c -> new ScoredGrade(RelevanceGrade.CORRECT, (float) c.relevanceScore()))
                                                                .toList();

        var chunks = List.of(
                new RetrievedChunk("text1", "doc1", 0.9, Map.of()),
                new RetrievedChunk("text2", "doc2", 0.5, Map.of()));

        List<ScoredGrade> results = evaluator.evaluateChunks("query", chunks);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).grade()).isEqualTo(RelevanceGrade.CORRECT);
        assertThat(results.get(0).score()).isEqualTo(0.9f, within(0.001f));
    }

    @Test
    void evaluateChunksEmptyListReturnsEmpty() {
        RelevanceEvaluator evaluator = (query, chunks) -> List.of();
        assertThat(evaluator.evaluateChunks("query", List.of())).isEmpty();
    }
}

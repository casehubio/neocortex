package io.casehub.neocortex.rag.testing;

import io.casehub.neocortex.rag.RelevanceGrade;
import io.casehub.neocortex.rag.RetrievedChunk;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRelevanceEvaluatorTest {

    @Test
    void defaultConstructorReturnsCorrect() {
        var evaluator = new InMemoryRelevanceEvaluator();
        var chunks    = List.of(new RetrievedChunk("text", "doc1", 0.9, Map.of()));
        var results   = evaluator.evaluateChunks("query", chunks);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).grade()).isEqualTo(RelevanceGrade.CORRECT);
        assertThat(results.get(0).score()).isNaN();
    }

    @Test
    void returningFactoryReturnsConfiguredGrade() {
        var evaluator = InMemoryRelevanceEvaluator.returning(RelevanceGrade.INCORRECT);
        var chunks    = List.of(new RetrievedChunk("text", "doc1", 0.5, Map.of()));
        var results   = evaluator.evaluateChunks("query", chunks);
        assertThat(results.get(0).grade()).isEqualTo(RelevanceGrade.INCORRECT);
    }

    @Test
    void evaluateChunksReturnsConfiguredGradeForAll() {
        var evaluator = InMemoryRelevanceEvaluator.returning(RelevanceGrade.AMBIGUOUS);
        var chunks = List.of(
                new RetrievedChunk("a", "d1", 0.9, Map.of()),
                new RetrievedChunk("b", "d2", 0.5, Map.of()));
        var results = evaluator.evaluateChunks("query", chunks);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(sg -> sg.grade() == RelevanceGrade.AMBIGUOUS);
        assertThat(results).allMatch(sg -> Float.isNaN(sg.score()));
    }
}

package io.casehub.neocortex.rag.testing;

import io.casehub.neocortex.rag.RelevanceEvaluator;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.ScoredGrade;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class RelevanceEvaluatorContractTest {

    protected abstract RelevanceEvaluator evaluator();

    protected RetrievedChunk chunk(String docId, double score) {
        return new RetrievedChunk("content-" + docId, docId, score, Map.of());
    }

    @Test
    void evaluateChunks_emptyListReturnsEmpty() {
        var result = evaluator().evaluateChunks("query", List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void evaluateChunks_returnsSameSizeAsinput() {
        var chunks = List.of(chunk("d1", 0.9), chunk("d2", 0.5), chunk("d3", 0.1));
        var result = evaluator().evaluateChunks("query", chunks);
        assertThat(result).hasSize(3);
    }

    @Test
    void evaluateChunks_allGradesNonNull() {
        var chunks = List.of(chunk("d1", 0.9), chunk("d2", 0.5));
        var result = evaluator().evaluateChunks("query", chunks);
        for (ScoredGrade sg : result) {
            assertThat(sg.grade()).isNotNull();
        }
    }

    @Test
    void evaluateChunks_singleChunk() {
        var result = evaluator().evaluateChunks("query", List.of(chunk("d1", 0.8)));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().grade()).isNotNull();
    }
}

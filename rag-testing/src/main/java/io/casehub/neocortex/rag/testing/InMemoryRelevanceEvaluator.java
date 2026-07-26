package io.casehub.neocortex.rag.testing;

import io.casehub.neocortex.rag.RelevanceEvaluator;
import io.casehub.neocortex.rag.RelevanceGrade;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.ScoredGrade;
import java.util.List;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryRelevanceEvaluator implements RelevanceEvaluator {

    private final RelevanceGrade fixedGrade;

    public InMemoryRelevanceEvaluator() {
        this.fixedGrade = RelevanceGrade.CORRECT;
    }

    private InMemoryRelevanceEvaluator(RelevanceGrade grade) {
        this.fixedGrade = grade;
    }

    public static InMemoryRelevanceEvaluator returning(RelevanceGrade grade) {
        return new InMemoryRelevanceEvaluator(grade);
    }

    @Override
    public List<ScoredGrade> evaluateChunks(String query, List<RetrievedChunk> chunks) {
        return chunks.stream()
                     .map(c -> new ScoredGrade(fixedGrade, Float.NaN))
                     .toList();
    }
}

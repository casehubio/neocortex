package io.casehub.neocortex.rag.crossencoder.corrective;

import io.casehub.neocortex.inference.tasks.CrossEncoderReranker;
import io.casehub.neocortex.inference.tasks.RankedResult;
import io.casehub.neocortex.rag.RelevanceEvaluator;
import io.casehub.neocortex.rag.RelevanceGrade;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.ScoredGrade;

import java.util.List;

public final class CrossEncoderRelevanceEvaluator implements RelevanceEvaluator {

    private final CrossEncoderReranker reranker;
    private final double               correctThreshold;
    private final double               incorrectThreshold;

    public CrossEncoderRelevanceEvaluator(CrossEncoderReranker reranker,
                                          double correctThreshold,
                                          double incorrectThreshold) {
        if (reranker == null) {throw new IllegalArgumentException("reranker must not be null");}
        if (incorrectThreshold > correctThreshold) {
            throw new IllegalArgumentException(
                    "incorrectThreshold (" + incorrectThreshold
                    + ") must not exceed correctThreshold (" + correctThreshold + ")");
        }
        this.reranker           = reranker;
        this.correctThreshold   = correctThreshold;
        this.incorrectThreshold = incorrectThreshold;
    }

    @Override
    public List<ScoredGrade> evaluateChunks(String query, List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {return List.of();}
        List<String> contents = chunks.stream()
                                      .map(RetrievedChunk::content).toList();
        List<RankedResult> ranked  = reranker.rerank(query, contents);
        ScoredGrade[]      results = new ScoredGrade[contents.size()];
        for (RankedResult r : ranked) {
            results[r.originalIndex()] = new ScoredGrade(
                    gradeFromScore(r.score()), r.score());
        }
        return List.of(results);
    }

    private RelevanceGrade gradeFromScore(float score) {
        if (score >= (float) correctThreshold) {return RelevanceGrade.CORRECT;}
        if (score <= (float) incorrectThreshold) {return RelevanceGrade.INCORRECT;}
        return RelevanceGrade.AMBIGUOUS;
    }
}

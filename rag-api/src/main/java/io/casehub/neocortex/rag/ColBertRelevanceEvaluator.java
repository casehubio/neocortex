package io.casehub.neocortex.rag;

import java.util.List;

public final class ColBertRelevanceEvaluator implements RelevanceEvaluator {

    private final double correctThreshold;
    private final double incorrectThreshold;

    public ColBertRelevanceEvaluator(double correctThreshold, double incorrectThreshold) {
        if (incorrectThreshold > correctThreshold)
            throw new IllegalArgumentException(
                "incorrectThreshold (" + incorrectThreshold
                    + ") must not exceed correctThreshold (" + correctThreshold + ")");
        this.correctThreshold = correctThreshold;
        this.incorrectThreshold = incorrectThreshold;
    }

    @Override
    public List<ScoredGrade> evaluateChunks(String query, List<RetrievedChunk> chunks) {
        return chunks.stream()
            .map(c -> new ScoredGrade(
                gradeFromScore(c.relevanceScore()),
                (float) c.relevanceScore()))
            .toList();
    }

    private RelevanceGrade gradeFromScore(double score) {
        if (score >= correctThreshold)   return RelevanceGrade.CORRECT;
        if (score <= incorrectThreshold) return RelevanceGrade.INCORRECT;
        return RelevanceGrade.AMBIGUOUS;
    }
}

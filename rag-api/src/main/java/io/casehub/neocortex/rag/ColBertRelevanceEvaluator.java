package io.casehub.neocortex.rag;

import java.util.List;

public final class ColBertRelevanceEvaluator implements RelevanceEvaluator {
    public static ColBertRelevanceEvaluator calibrate(List<Double> sampleScores) {
        return calibrate(sampleScores, 75, 25);
    }

    public static ColBertRelevanceEvaluator calibrate(List<Double> sampleScores,
                                                      int correctPercentile, int incorrectPercentile) {
        if (sampleScores.isEmpty()) {throw new IllegalArgumentException("sampleScores must not be empty");}
        if (correctPercentile < 0 || correctPercentile > 100) {
            throw new IllegalArgumentException("correctPercentile must be 0-100");
        }
        if (incorrectPercentile < 0 || incorrectPercentile > 100) {
            throw new IllegalArgumentException("incorrectPercentile must be 0-100");
        }
        if (incorrectPercentile > correctPercentile) {
            throw new IllegalArgumentException("incorrectPercentile must not exceed correctPercentile");
        }
        double[] sorted = sampleScores.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return new ColBertRelevanceEvaluator(
                percentile(sorted, correctPercentile),
                percentile(sorted, incorrectPercentile));
    }


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

    private static double percentile(double[] sorted, int p) {
        if (sorted.length == 1) {return sorted[0];}
        double rank     = p / 100.0 * (sorted.length - 1);
        int    lower    = (int) Math.floor(rank);
        int    upper    = Math.min(lower + 1, sorted.length - 1);
        double fraction = rank - lower;
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
    }
}

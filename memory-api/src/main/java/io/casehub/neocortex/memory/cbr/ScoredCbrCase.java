package io.casehub.neocortex.memory.cbr;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ScoredCbrCase<C extends CbrCase>(C cbrCase, String caseId, double score, boolean reranked,
                                               Map<String, Double> featureSimilarities, Instant storedAt,
                                               io.casehub.platform.api.path.Path scope) {
    public ScoredCbrCase {
        Objects.requireNonNull(cbrCase, "cbrCase required");
        if (!(score >= -1.0 && score <= 1.0)) {
            throw new IllegalArgumentException("score must be in [-1,1], got: " + score);
        }
        featureSimilarities = featureSimilarities != null ? Map.copyOf(featureSimilarities) : Map.of();
        if (scope == null) {scope = io.casehub.platform.api.path.Path.root();}
    }

    public ScoredCbrCase(C cbrCase, String caseId, double score) {
        this(cbrCase, caseId, score, false, Map.of(), null, io.casehub.platform.api.path.Path.root());
    }

    public ScoredCbrCase(C cbrCase, double score) {
        this(cbrCase, null, score, false, Map.of(), null, io.casehub.platform.api.path.Path.root());
    }

    public ScoredCbrCase(C cbrCase, double score, boolean reranked) {
        this(cbrCase, null, score, reranked, Map.of(), null, io.casehub.platform.api.path.Path.root());
    }

    public ScoredCbrCase(C cbrCase, double score, boolean reranked,
                         Map<String, Double> featureSimilarities) {
        this(cbrCase, null, score, reranked, featureSimilarities, null, io.casehub.platform.api.path.Path.root());
    }

    public ScoredCbrCase<C> withScore(double newScore) {
        return new ScoredCbrCase<>(cbrCase, caseId, newScore, reranked, featureSimilarities, storedAt, scope);
    }

    public ScoredCbrCase<C> withReranked() {
        return new ScoredCbrCase<>(cbrCase, caseId, score, true, featureSimilarities, storedAt, scope);
    }
}

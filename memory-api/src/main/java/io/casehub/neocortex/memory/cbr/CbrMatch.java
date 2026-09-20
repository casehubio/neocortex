package io.casehub.neocortex.memory.cbr;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record CbrMatch<C extends CbrRecord>(C cbrRecord, String caseId, String caseType, double score,
                                            boolean reranked,
                                            Map<String, Double> featureSimilarities, Instant storedAt,
                                            io.casehub.platform.api.path.Path scope, Double trustTrajectory) {
    public CbrMatch {
        Objects.requireNonNull(cbrRecord, "cbrRecord required");
        Objects.requireNonNull(caseType, "caseType required");
        if (!(score >= -1.0 && score <= 1.0)) {
            throw new IllegalArgumentException("score must be in [-1,1], got: " + score);
        }
        featureSimilarities = featureSimilarities != null ? Map.copyOf(featureSimilarities) : Map.of();
        if (scope == null) {scope = io.casehub.platform.api.path.Path.root();}
    }

    public CbrMatch(C cbrRecord, String caseId, String caseType, double score) {
        this(cbrRecord, caseId, caseType, score, false, Map.of(), null, io.casehub.platform.api.path.Path.root(), null);
    }

    public CbrMatch(C cbrRecord, String caseType, double score) {
        this(cbrRecord, null, caseType, score, false, Map.of(), null, io.casehub.platform.api.path.Path.root(), null);
    }

    public CbrMatch(C cbrRecord, String caseType, double score, boolean reranked) {
        this(cbrRecord, null, caseType, score, reranked, Map.of(), null, io.casehub.platform.api.path.Path.root(), null);
    }

    public CbrMatch(C cbrRecord, String caseType, double score, boolean reranked,
                    Map<String, Double> featureSimilarities) {
        this(cbrRecord, null, caseType, score, reranked, featureSimilarities, null, io.casehub.platform.api.path.Path.root(), null);
    }

    public CbrMatch<C> withScore(double newScore) {
        return new CbrMatch<>(cbrRecord, caseId, caseType, newScore, reranked, featureSimilarities, storedAt, scope, trustTrajectory);
    }

    public CbrMatch<C> withReranked() {
        return new CbrMatch<>(cbrRecord, caseId, caseType, score, true, featureSimilarities, storedAt, scope, trustTrajectory);
    }

    public CbrMatch<C> withTrustTrajectory(Double delta) {
        return new CbrMatch<>(cbrRecord, caseId, caseType, score, reranked, featureSimilarities, storedAt, scope, delta);
    }

    public CbrMatch<C> withCaseType(String caseType) {
        return new CbrMatch<>(cbrRecord, caseId, caseType, score, reranked, featureSimilarities, storedAt, scope, trustTrajectory);
    }
}

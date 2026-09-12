package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.DelegatingCbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TrustWeightingFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

public class TrustWeightedCbrCaseMemoryStore extends DelegatingCbrCaseMemoryStore {

    private final TrustWeightingFunction weightingFunction;
    private final AgentTrustProvider trustProvider;

    public TrustWeightedCbrCaseMemoryStore(CbrCaseMemoryStore delegate,
                                           TrustWeightingFunction weightingFunction,
                                           AgentTrustProvider trustProvider) {
        super(delegate);
        this.weightingFunction = weightingFunction;
        this.trustProvider = trustProvider;
    }

    @Override
    public <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseClass) {
        List<ScoredCbrCase<C>> results = delegate.retrieveSimilar(query, caseClass);
        if (results.isEmpty()) return results;

        Map<String, OptionalDouble> trajectoryCache = new HashMap<>();
        List<ScoredCbrCase<C>> weighted = new ArrayList<>(results.size());
        for (ScoredCbrCase<C> scored : results) {
            Double trust = scored.cbrCase().trustScore();
            if (trust == null) {
                weighted.add(scored);
                continue;
            }
            OptionalDouble trajectory = computeTrajectory(scored.cbrCase(), trajectoryCache);
            double newScore = weightingFunction.apply(scored.score(), trust, trajectory);
            Double delta = trajectory.isPresent() ? trajectory.getAsDouble() : null;
            weighted.add(scored.withScore(newScore).withTrustTrajectory(delta));
        }
        weighted.sort((a, b) -> Double.compare(b.score(), a.score()));
        return Collections.unmodifiableList(weighted);
    }

    private OptionalDouble computeTrajectory(CbrCase cbrCase,
                                              Map<String, OptionalDouble> cache) {
        if (trustProvider == null || cbrCase.producerAgentId() == null
                || cbrCase.trustScore() == null) {
            return OptionalDouble.empty();
        }
        String agentId = cbrCase.producerAgentId();
        OptionalDouble current = cache.computeIfAbsent(agentId,
                id -> trustProvider.currentTrustScore(id));
        if (current.isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of(current.getAsDouble() - cbrCase.trustScore());
    }
}

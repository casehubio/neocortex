package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.DelegatingCbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.TrustWeightingFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

public class TrustWeightedCbrRecordStore extends DelegatingCbrRecordStore {

    private final TrustWeightingFunction weightingFunction;
    private final AgentTrustProvider trustProvider;

    public TrustWeightedCbrRecordStore(CbrRecordStore delegate,
                                       TrustWeightingFunction weightingFunction,
                                       AgentTrustProvider trustProvider) {
        super(delegate);
        this.weightingFunction = weightingFunction;
        this.trustProvider = trustProvider;
    }

    @Override
    public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseClass) {
        List<CbrMatch<C>> results = delegate.retrieveSimilar(query, caseClass);
        if (results.isEmpty()) return results;

        Map<String, OptionalDouble> trajectoryCache = new HashMap<>();
        List<CbrMatch<C>>           weighted        = new ArrayList<>(results.size());
        for (CbrMatch<C> scored : results) {
            Double trust = scored.cbrRecord().trustScore();
            if (trust == null) {
                weighted.add(scored);
                continue;
            }
            OptionalDouble trajectory = computeTrajectory(scored.cbrRecord(), trajectoryCache);
            double newScore = weightingFunction.apply(scored.score(), trust, trajectory);
            Double delta = trajectory.isPresent() ? trajectory.getAsDouble() : null;
            weighted.add(scored.withScore(newScore).withTrustTrajectory(delta));
        }
        weighted.sort((a, b) -> Double.compare(b.score(), a.score()));
        return Collections.unmodifiableList(weighted);
    }

    private OptionalDouble computeTrajectory(CbrRecord cbrRecord,
                                             Map<String, OptionalDouble> cache) {
        if (trustProvider == null || cbrRecord.producerAgentId() == null
            || cbrRecord.trustScore() == null) {
            return OptionalDouble.empty();
        }
        String agentId = cbrRecord.producerAgentId();
        OptionalDouble current = cache.computeIfAbsent(agentId,
                id -> trustProvider.currentTrustScore(id));
        if (current.isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of(current.getAsDouble() - cbrRecord.trustScore());
    }
}

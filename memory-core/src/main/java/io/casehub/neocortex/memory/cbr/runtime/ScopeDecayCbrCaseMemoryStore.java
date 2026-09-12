package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.DelegatingCbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.ScopeDecay;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScopeDecayCbrCaseMemoryStore extends DelegatingCbrCaseMemoryStore {

    public ScopeDecayCbrCaseMemoryStore(CbrCaseMemoryStore delegate) {
        super(delegate);
    }

    @Override
    public <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseType) {
        List<ScoredCbrCase<C>> results = delegate.retrieveSimilar(query, caseType);
        if (query.scopeDecay() == null) {
            return results;
        }
        ScopeDecay decay = query.scopeDecay();
        int queryDepth = query.scope().depth();
        List<ScoredCbrCase<C>> decayed = new ArrayList<>(results.size());
        for (var scored : results) {
            int depthDistance = queryDepth - scored.scope().depth();
            double factor = decay.factor(depthDistance);
            double adjustedScore = scored.score() * factor;
            if (adjustedScore >= query.minSimilarity()) {
                decayed.add(scored.withScore(adjustedScore));
            }
        }
        decayed.sort((a, b) -> Double.compare(b.score(), a.score()));
        return Collections.unmodifiableList(decayed);
    }
}

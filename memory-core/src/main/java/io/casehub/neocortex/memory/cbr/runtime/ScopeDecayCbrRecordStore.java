package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.DelegatingCbrRecordStore;
import io.casehub.neocortex.memory.cbr.ScopeDecay;
import io.casehub.neocortex.memory.cbr.CbrMatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScopeDecayCbrRecordStore extends DelegatingCbrRecordStore {

    public ScopeDecayCbrRecordStore(CbrRecordStore delegate) {
        super(delegate);
    }

    @Override
    public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseType) {
        List<CbrMatch<C>> results = delegate.retrieveSimilar(query, caseType);
        if (query.scopeDecay() == null) {
            return results;
        }
        ScopeDecay        decay      = query.scopeDecay();
        int               queryDepth = query.scope().depth();
        List<CbrMatch<C>> decayed    = new ArrayList<>(results.size());
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

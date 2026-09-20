package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.DelegatingCbrRecordStore;
import io.casehub.neocortex.memory.cbr.TemporalDecay;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TemporalDecayCbrRecordStore extends DelegatingCbrRecordStore {

    public TemporalDecayCbrRecordStore(CbrRecordStore delegate) {
        super(delegate);
    }

    @Override
    public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseType) {
        List<CbrMatch<C>> results = delegate.retrieveSimilar(query, caseType);
        if (query.temporalDecay() == null) {
            return results;
        }
        Instant           now     = Instant.now();
        TemporalDecay     decay   = query.temporalDecay();
        List<CbrMatch<C>> decayed = new ArrayList<>(results.size());
        for (var scored : results) {
            double factor = (scored.storedAt() != null)
                    ? decay.factor(scored.storedAt(), now) : 1.0;
            double adjustedScore = scored.score() * factor;
            if (adjustedScore >= query.minSimilarity()) {
                decayed.add(scored.withScore(adjustedScore));
            }
        }
        decayed.sort((a, b) -> Double.compare(b.score(), a.score()));
        return Collections.unmodifiableList(decayed);
    }
}

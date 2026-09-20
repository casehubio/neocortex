package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.DelegatingCbrRecordStore;
import io.casehub.neocortex.memory.cbr.OutcomeWeightingFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OutcomeWeightingCbrRecordStore extends DelegatingCbrRecordStore {

    private final OutcomeWeightingFunction weightingFunction;

    public OutcomeWeightingCbrRecordStore(CbrRecordStore delegate,
                                          OutcomeWeightingFunction weightingFunction) {
        super(delegate);
        this.weightingFunction = weightingFunction;
    }

    @Override
    public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseClass) {
        List<CbrMatch<C>> results = delegate.retrieveSimilar(query, caseClass);
        if (results.isEmpty()) {
            return results;
        }
        List<CbrMatch<C>> weighted = new ArrayList<>(results.size());
        for (CbrMatch<C> scored : results) {
            double confidence = scored.cbrRecord().confidence() != null
                                ? scored.cbrRecord().confidence().value() : 1.0;
            double newScore = weightingFunction.apply(scored.score(), confidence);
            weighted.add(scored.withScore(newScore));
        }
        weighted.sort((a, b) -> Double.compare(b.score(), a.score()));
        return Collections.unmodifiableList(weighted);
    }
}

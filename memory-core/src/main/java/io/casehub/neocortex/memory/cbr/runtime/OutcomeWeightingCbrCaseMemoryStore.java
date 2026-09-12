package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.DelegatingCbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.OutcomeWeightingFunction;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OutcomeWeightingCbrCaseMemoryStore extends DelegatingCbrCaseMemoryStore {

    private final OutcomeWeightingFunction weightingFunction;

    public OutcomeWeightingCbrCaseMemoryStore(CbrCaseMemoryStore delegate,
                                              OutcomeWeightingFunction weightingFunction) {
        super(delegate);
        this.weightingFunction = weightingFunction;
    }

    @Override
    public <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseClass) {
        List<ScoredCbrCase<C>> results = delegate.retrieveSimilar(query, caseClass);
        if (results.isEmpty()) {
            return results;
        }
        List<ScoredCbrCase<C>> weighted = new ArrayList<>(results.size());
        for (ScoredCbrCase<C> scored : results) {
            double confidence = scored.cbrCase().confidence() != null
                                ? scored.cbrCase().confidence().value() : 1.0;
            double newScore = weightingFunction.apply(scored.score(), confidence);
            weighted.add(scored.withScore(newScore));
        }
        weighted.sort((a, b) -> Double.compare(b.score(), a.score()));
        return Collections.unmodifiableList(weighted);
    }
}

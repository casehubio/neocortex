package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.ModulationFactor;
import io.casehub.neocortex.cognitive.RetrievalModulator;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryQuery;

import java.util.List;

public class CognitiveMemoryRetriever {

    private final CaseMemoryStore store;
    private final List<ModulationFactor<Memory>> factors;

    public CognitiveMemoryRetriever(CaseMemoryStore store,
                                     List<ModulationFactor<Memory>> factors) {
        this.store = store;
        this.factors = factors.isEmpty() ? List.of() : List.copyOf(factors);
    }

    public List<Memory> query(MemoryQuery query) {
        List<Memory> results = store.query(query);
        if (factors.isEmpty()) return results;
        return RetrievalModulator.modulate(results, ModulationProfiles.MEMORY, factors);
    }
}

package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.ModulationFactor;
import io.casehub.neocortex.cognitive.RetrievalModulator;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryQuery;

import java.util.ArrayList;
import java.util.List;

public class CognitiveMemoryRetriever {

    private final CaseMemoryStore store;
    private final List<ModulationFactor<Memory>> staticFactors;
    private final List<ContextualModulationFactory<Memory>> contextualFactories;
    private volatile ModulationContext                      tickContext;


    public CognitiveMemoryRetriever(CaseMemoryStore store,
                                     List<ModulationFactor<Memory>> staticFactors,
                                     List<ContextualModulationFactory<Memory>> contextualFactories) {
        this.store = store;
        this.staticFactors = staticFactors.isEmpty() ? List.of() : List.copyOf(staticFactors);
        this.contextualFactories = contextualFactories.isEmpty() ? List.of() : List.copyOf(contextualFactories);
    }

    public CognitiveMemoryRetriever(CaseMemoryStore store,
                                     List<ModulationFactor<Memory>> factors) {
        this(store, factors, List.of());
    }


    public void setTickContext(ModulationContext context) {
        this.tickContext = context;
    }

    public List<Memory> query(MemoryQuery query, ModulationContext context) {
        List<Memory> results = store.query(query);
        var factors = buildFactors(context);
        if (factors.isEmpty()) return results;
        return RetrievalModulator.modulate(results, ModulationProfiles.MEMORY, factors);
    }

    public List<Memory> query(MemoryQuery query) {
        var ctx = tickContext;
        if (ctx == null) {
            ctx = new ModulationContext(null, query.tenantId(), null, null, null);
        }
        return query(query, ctx);
    }

    private List<ModulationFactor<Memory>> buildFactors(ModulationContext ctx) {
        if (contextualFactories.isEmpty()) return staticFactors;
        var all = new ArrayList<>(staticFactors);
        for (var factory : contextualFactories) {
            var factor = factory.create(ctx);
            if (factor != null) all.add(factor);
        }
        return all;
    }
}

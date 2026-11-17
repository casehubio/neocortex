package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import io.casehub.neocortex.cognitive.CognitiveResourcePoolConfig;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.cognitive.RecurringThoughtConfig;
import io.casehub.neocortex.cognitive.SearchBudgetConfig;

public record CognitiveResourceDefaults(
        CognitiveCapacityConfig capacityConfig,
        CognitiveResourcePoolConfig cognitiveResourcePoolConfig,
        SearchBudgetConfig searchBudgetConfig,
        RecurringThoughtConfig recurringThoughtConfig,
        HabituationConfig habituationConfig
) {
    public static CognitiveResourceDefaults empty() {
        return new CognitiveResourceDefaults(null, null, null, null, null);
    }

    public CognitiveResourceDefaults mergeWith(CognitiveResourceDefaults derived) {
        if (derived == null) return this;
        return new CognitiveResourceDefaults(
                mergeCapacity(capacityConfig, derived.capacityConfig()),
                cognitiveResourcePoolConfig != null ? cognitiveResourcePoolConfig : derived.cognitiveResourcePoolConfig(),
                searchBudgetConfig != null ? searchBudgetConfig : derived.searchBudgetConfig(),
                recurringThoughtConfig != null ? recurringThoughtConfig : derived.recurringThoughtConfig(),
                habituationConfig != null ? habituationConfig : derived.habituationConfig());
    }

    private static CognitiveCapacityConfig mergeCapacity(CognitiveCapacityConfig explicit, CognitiveCapacityConfig derived) {
        if (explicit == null) return derived;
        if (derived == null) return explicit;
        return explicit.mergeWith(derived);
    }
}

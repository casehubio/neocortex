package io.casehub.neocortex.memory.personality;

import io.casehub.neocortex.memory.MemoryDomain;
import java.util.Map;
import java.util.Objects;

public record PersonalityWeights(Map<MemoryDomain, Double> domainWeights) {
    public PersonalityWeights {
        Objects.requireNonNull(domainWeights, "domainWeights required");
        for (var entry : domainWeights.entrySet()) {
            if (entry.getValue() <= 0.0)
                throw new IllegalArgumentException(
                    "weight for domain '" + entry.getKey().name() + "' must be > 0, got " + entry.getValue());
        }
        domainWeights = Map.copyOf(domainWeights);
    }

    public double getWeight(MemoryDomain domain) {
        return domainWeights.getOrDefault(domain, 1.0);
    }
}

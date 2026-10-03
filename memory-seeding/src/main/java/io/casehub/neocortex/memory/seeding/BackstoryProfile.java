package io.casehub.neocortex.memory.seeding;

import io.casehub.neocortex.cognition.need.NeedTier;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record BackstoryProfile(
    String agentId,
    String tenantId,
    List<CatalogueSelection> selections,
    Map<NeedTier, Double> needSatisfaction
) {
    public BackstoryProfile {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(selections, "selections required");
        if (selections.isEmpty()) throw new IllegalArgumentException("selections must not be empty");
        selections = List.copyOf(selections);
        needSatisfaction = needSatisfaction != null ? Map.copyOf(needSatisfaction) : Map.of();
    }

    public record CatalogueSelection(
        String entryId,
        Double intensityOverride,
        Integer repetitionOverride
    ) {
        public CatalogueSelection {
            Objects.requireNonNull(entryId, "entryId required");
        }
    }
}

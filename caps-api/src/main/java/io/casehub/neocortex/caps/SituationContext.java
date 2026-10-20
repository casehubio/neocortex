package io.casehub.neocortex.caps;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SituationContext(
    List<SituationActivation> activations,
    Map<String, Double> confidenceMap
) {
    public static SituationContext from(List<SituationActivation> activations) {
        var map = new HashMap<String, Double>();
        for (var a : activations) { map.put(a.nodeId(), a.confidence()); }
        return new SituationContext(List.copyOf(activations), Map.copyOf(map));
    }

    public static final SituationContext EMPTY = new SituationContext(List.of(), Map.of());

    public boolean isEmpty() { return activations.isEmpty(); }
}

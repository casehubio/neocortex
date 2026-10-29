package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.cognitive.index.RelationalSchema;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RelationalBiasing {

    private RelationalBiasing() {}

    // Positive scale = boost with trust; negative = dampen with trust.
    // Provenance: 0.30 matches secure_attachment→self_worth topology weight
    // (van IJzendoorn 1995, r=.24-.32). Others scaled conservatively below.
    private static final Map<String, Double> TRUST_SCALING = Map.of(
            "secure_attachment",     0.3,
            "rejection",            -0.2,
            "betrayal",             -0.25,
            "psychological_threat", -0.15
    );

    public static List<SituationActivation> apply(
            @Nullable RelationalSchema schema,
            List<SituationActivation> activations) {
        if (schema == null || activations.isEmpty()) {
            return activations;
        }

        double trust = schema.trust();
        if (trust == 0.0) {
            return activations;
        }

        var result = new ArrayList<SituationActivation>(activations.size());
        for (var activation : activations) {
            var scale = TRUST_SCALING.get(activation.nodeId());
            if (scale == null) {
                result.add(activation);
            } else {
                double biased = activation.confidence() * (1.0 + trust * scale);
                result.add(new SituationActivation(
                        activation.nodeId(), biased, activation.source()));
            }
        }
        return List.copyOf(result);
    }
}

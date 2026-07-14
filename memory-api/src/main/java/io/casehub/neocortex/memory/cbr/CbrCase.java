package io.casehub.neocortex.memory.cbr;

import java.util.Map;

public interface CbrCase {
    String cbrType();
    String problem();
    String solution();
    String outcome();
    Double confidence();

    default Map<String, FeatureValue> features() { return Map.of(); }

    CbrCase withOutcome(String outcome, Double confidence);

    default CbrCase withFeatures(Map<String, FeatureValue> features) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support withFeatures");
    }


}

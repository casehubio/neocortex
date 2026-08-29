package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.cognitive.Confidence;

import java.util.Map;

public interface CbrCase {
    String cbrType();

    String problem();

    String solution();

    String outcome();

    Confidence confidence();

    default Double trustScore()                  {return null;}

    default String producerAgentId()             {return null;}


    default Map<String, FeatureValue> features() {return Map.of();}

    CbrCase withOutcome(String outcome, Confidence confidence);

    default CbrCase withFeatures(Map<String, FeatureValue> features) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support withFeatures");
    }


}

package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.cognitive.Confidence;

import java.util.Map;

public interface CbrRecord {
    String recordType();

    String problem();

    String solution();

    String outcome();

    Confidence confidence();

    default Double trustScore()                  {return null;}

    default String producerAgentId()             {return null;}


    default Map<String, FeatureValue> features() {return Map.of();}

    CbrRecord withOutcome(String outcome, Confidence confidence);

    default CbrRecord withFeatures(Map<String, FeatureValue> features) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support withFeatures");
    }


}

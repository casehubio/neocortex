package io.casehub.neocortex.cognition.situational;

import java.util.List;
import java.util.Map;

public sealed interface SignalConfig {

    double weight();

    record SituationInputSignal(
            List<String> nodes,
            Aggregation aggregation,
            double weight
    ) implements SignalConfig {}

    record CapsMediatingSignal(
            List<String> nodes,
            Aggregation aggregation,
            SignalTransform transform,
            double weight
    ) implements SignalConfig {}

    record SecSignal(
            List<SecFactor> product,
            double weight
    ) implements SignalConfig {}

    record MoodSignal(
            String axis,
            SignalTransform transform,
            double weight
    ) implements SignalConfig {}

    record MoodBandSignal(
            String axis,
            ParameterisedTransform.Band band,
            double weight
    ) implements SignalConfig {}

    record SecFactor(
            String dimension,
            SignalTransform transform
    ) {
        public SecFactor(String dimension) {
            this(dimension, SignalTransform.IDENTITY);
        }
    }

    record ChallengeSkillFactor(
            String challengeDim,
            String skillDim
    ) {}

    record RelationalConfig(
            String field,
            SignalTransform transform
    ) {}

    enum Aggregation { MAX, MEAN }
}

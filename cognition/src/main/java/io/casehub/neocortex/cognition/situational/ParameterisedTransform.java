package io.casehub.neocortex.cognition.situational;

public sealed interface ParameterisedTransform {

    double apply(double... inputs);

    record Band(double centre, double width) implements ParameterisedTransform {
        @Override
        public double apply(double... inputs) {
            double x = inputs[0];
            return Math.max(0, 1.0 - Math.abs(x - centre) / width);
        }
    }

    record ChallengeSkill() implements ParameterisedTransform {
        @Override
        public double apply(double... inputs) {
            double challenge = inputs[0];
            double skill = inputs[1];
            return Math.sqrt(challenge * skill) * (1.0 - Math.abs(challenge - skill));
        }
    }
}

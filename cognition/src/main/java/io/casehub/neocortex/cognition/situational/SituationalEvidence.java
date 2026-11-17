package io.casehub.neocortex.cognition.situational;

import java.util.Map;

public record SituationalEvidence(
        Map<String, Double> situationInputActivations,
        Map<String, Double> capsMediating,
        Map<String, Double> capsRestReference,
        Map<String, Double> secDimensions,
        double moodPleasure,
        double moodArousal,
        double moodDominance,
        double relationalTrust,
        boolean hasInterlocutor
) {
    public static SituationalEvidence empty() {
        return new SituationalEvidence(Map.of(), Map.of(), Map.of(), Map.of(),
                0, 0, 0, Double.NaN, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, Double> situationInputs = Map.of();
        private Map<String, Double> capsMediating = Map.of();
        private Map<String, Double> capsRest = Map.of();
        private Map<String, Double> secDimensions = Map.of();
        private double pleasure = 0, arousal = 0, dominance = 0;
        private double trust = Double.NaN;
        private boolean hasInterlocutor = false;

        public Builder situationInputs(Map<String, Double> s) { this.situationInputs = s; return this; }
        public Builder capsMediating(Map<String, Double> m) { this.capsMediating = m; return this; }
        public Builder capsRest(Map<String, Double> r) { this.capsRest = r; return this; }
        public Builder secDimensions(Map<String, Double> d) { this.secDimensions = d; return this; }
        public Builder mood(double p, double a, double d) { this.pleasure = p; this.arousal = a; this.dominance = d; return this; }
        public Builder interlocutor(double trust) { this.trust = trust; this.hasInterlocutor = true; return this; }

        public SituationalEvidence build() {
            return new SituationalEvidence(situationInputs, capsMediating, capsRest, secDimensions,
                    pleasure, arousal, dominance, trust, hasInterlocutor);
        }
    }
}

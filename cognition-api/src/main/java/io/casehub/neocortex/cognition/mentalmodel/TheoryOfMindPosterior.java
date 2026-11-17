package io.casehub.neocortex.cognition.mentalmodel;

public record TheoryOfMindPosterior(
        double positive,
        double neutral,
        double distressed) {

    public TheoryOfMindPosterior {
        if (positive < 0 || neutral < 0 || distressed < 0) {
            throw new IllegalArgumentException("probabilities must be non-negative");
        }
        double sum = positive + neutral + distressed;
        if (Math.abs(sum - 1.0) > 0.01) {
            throw new IllegalArgumentException("probabilities must sum to 1.0, was " + sum);
        }
    }

    public String dominantValence() {
        if (positive >= neutral && positive >= distressed) return "POSITIVE";
        if (distressed >= neutral) return "DISTRESSED";
        return "NEUTRAL";
    }

    public double dominantProbability() {
        return Math.max(positive, Math.max(neutral, distressed));
    }
}

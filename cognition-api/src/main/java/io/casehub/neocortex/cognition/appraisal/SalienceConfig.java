package io.casehub.neocortex.cognition.appraisal;

public record SalienceConfig(
        double salienceThreshold) {
    public static SalienceConfig defaults() {
        return new SalienceConfig(0.0);
    }
}

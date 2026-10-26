package io.quarkmind.inference.bayes;

public record NoisyMaxParent(String parentName, double[] distribution) {
    public NoisyMaxParent {
        distribution = distribution.clone();
    }
}

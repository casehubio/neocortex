package io.casehub.neocortex.cognitive;

@FunctionalInterface
public interface ModulationFactor<T> {
    double apply(T item, ModulationProfile<T> profile);
}

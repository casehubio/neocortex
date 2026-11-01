package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.ModulationFactor;

@FunctionalInterface
public interface ContextualModulationFactory<T> {
    ModulationFactor<T> create(ModulationContext context);
}

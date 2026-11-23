package io.casehub.neocortex.cognition.dissonance;

import java.util.List;

@FunctionalInterface
public interface DissonanceActivationSource {
    List<TensionActivation> activeTensions(String cacheKey);
}
